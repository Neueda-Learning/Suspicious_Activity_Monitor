package com.bank.aml.service;

import com.bank.aml.config.AppProperties;
import com.bank.aml.domain.AiDraftEntity;
import com.bank.aml.domain.AlertEntity;
import com.bank.aml.domain.CaseEntity;
import com.bank.aml.domain.CustomerEntity;
import com.bank.aml.domain.TransactionEntity;
import com.bank.aml.repo.AiDraftRepository;
import com.bank.aml.repo.AlertRepository;
import com.bank.aml.repo.CaseRepository;
import com.bank.aml.repo.CustomerRepository;
import com.bank.aml.repo.TransactionRepository;
import com.bank.aml.util.Jsons;
import com.bank.aml.web.dto.AiDraftAcceptRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiDraftService {
    private final AiDraftRepository aiDraftRepository;
    private final CaseRepository caseRepository;
    private final CustomerRepository customerRepository;
    private final AlertRepository alertRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;
    private final AppProperties appProperties;
    private final RestClient.Builder restClientBuilder;

    @Transactional
    public AiDraftEntity generate(Long caseId) {
        CaseEntity c = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found"));
        CustomerEntity customer = customerRepository.findById(c.getCustomerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        List<AlertEntity> alerts = alertRepository.findByCaseId(caseId);
        Instant ws = c.getWindowStart() != null ? c.getWindowStart() : c.getOpenedAt().minusSeconds(86400);
        Instant we = c.getWindowEnd() != null ? c.getWindowEnd() : c.getOpenedAt();
        List<TransactionEntity> txns =
                transactionRepository.findByCustomerIdAndExecutedAtBetween(c.getCustomerId(), ws, we);

        ObjectNode input = buildModelInput(c, customer, alerts, txns);

        ObjectNode template = buildTemplate(c, customer, alerts, txns);
        String raw = Jsons.pretty(template);
        boolean fallbackUsed = true;
        String modelName = "template";
        long elapsed = 0;

        Instant start = Instant.now();
        try {
            String llm = callOllama(input);
            if (llm != null && !llm.isBlank()) {
                JsonNode parsed = Jsons.MAPPER.readTree(extractJson(llm));
                boolean validShape = parsed.path("narrative").isTextual()
                        && parsed.path("confirmedObservations").isArray()
                        && parsed.path("unexplainedQuestions").isArray()
                        && parsed.path("suggestedNextChecks").isArray();
                if (validShape) {
                    ObjectNode normalized = normalizeDraftCitations(parsed, alerts, txns);
                    raw = Jsons.pretty(normalized);
                    fallbackUsed = false;
                    modelName = appProperties.getOllama().getModel();
                } else {
                    log.warn(
                            "Ollama response did not match the required draft schema "
                                    + "(narrative={}, observations={}, questions={}, checks={})",
                            parsed.path("narrative").getNodeType(),
                            parsed.path("confirmedObservations").getNodeType(),
                            parsed.path("unexplainedQuestions").getNodeType(),
                            parsed.path("suggestedNextChecks").getNodeType());
                }
            }
        } catch (Exception ex) {
            log.warn("Ollama fallback to template: {}", ex.getMessage());
        }
        elapsed = Duration.between(start, Instant.now()).toMillis();

        ObjectNode meta = Jsons.obj();
        meta.put("model", modelName);
        meta.put("promptVersion", "v2");
        meta.put("temperature", 0.2);
        meta.put("elapsedMs", elapsed);
        meta.put("fallbackUsed", fallbackUsed);

        AiDraftEntity draft = new AiDraftEntity();
        draft.setCaseId(caseId);
        draft.setInputSnapshotJson(input);
        draft.setRawOutput(raw);
        draft.setModelMetadataJson(meta);
        draft.setGeneratedAt(Instant.now());
        draft.setStatus("GENERATED");
        draft = aiDraftRepository.save(draft);
        auditService.record("AI_DRAFT_GENERATED", "AI_DRAFT", draft.getId(),
                Map.of("caseId", caseId, "fallbackUsed", fallbackUsed));
        return draft;
    }

    static ObjectNode buildModelInput(
            CaseEntity c, CustomerEntity customer, List<AlertEntity> alerts, List<TransactionEntity> txns) {
        ObjectNode input = Jsons.obj();
        Map<Long, String> refById = new HashMap<>();
        txns.forEach(t -> refById.put(t.getId(), t.getTxnRef()));
        input.put("caseRef", c.getCaseRef());
        input.put("priorityScore", c.getPriorityScore());
        input.put("priorityBand", c.getPriorityBand());
        input.put("customerRef", customer.getCustomerRef());
        input.put("customerName", customer.getName());
        input.put("crr", customer.getCrr());
        input.set("alerts", Jsons.toTree(alerts.stream().map(a -> Map.of(
                "evidenceId", alertEvidenceId(a),
                "ruleCode", a.getRuleCode(),
                "ruleName", a.getRuleName(),
                "points", a.getPoints(),
                "strength", a.getStrength(),
                "evidence", modelEvidence(a, refById)
        )).toList()));
        input.set("transactions", Jsons.toTree(txns.stream().map(t -> Map.of(
                "txnRef", t.getTxnRef(),
                "direction", t.getDirection(),
                "amountGbp", t.getAmountGbp(),
                "counterpartyName", t.getCounterpartyName() == null ? "" : t.getCounterpartyName(),
                "counterpartyCountry", t.getCounterpartyCountry() == null ? "" : t.getCounterpartyCountry(),
                "executedAt", t.getExecutedAt().toString()
        )).toList()));
        return input;
    }

    private static JsonNode modelEvidence(AlertEntity alert, Map<Long, String> refById) {
        JsonNode source = alert.getEvidenceSnapshot();
        if (source == null || !source.isObject()) return source;

        ObjectNode evidence = ((ObjectNode) source).deepCopy();
        JsonNode transactionIds = evidence.remove("transactionIds");
        if (transactionIds != null && transactionIds.isArray()) {
            ArrayNode transactionRefs = Jsons.arr();
            for (JsonNode id : transactionIds) {
                String ref = refById.get(id.asLong());
                if (ref != null) transactionRefs.add(ref);
            }
            evidence.set("transactionRefs", transactionRefs);
        }
        return evidence;
    }

    static ObjectNode normalizeDraftCitations(
            JsonNode parsed, List<AlertEntity> alerts, List<TransactionEntity> txns) {
        ObjectNode normalized = ((ObjectNode) parsed).deepCopy();
        Map<String, String> canonicalByAlias = new LinkedHashMap<>();

        alerts.forEach(a -> canonicalByAlias.put(alertEvidenceId(a), alertEvidenceId(a)));
        txns.forEach(t -> canonicalByAlias.put(t.getTxnRef(), t.getTxnRef()));

        // Older prompts exposed database primary keys. Prefer a matching transaction when an
        // alert and transaction happen to share the same numeric ID, because transaction IDs
        // were the values most often emitted as citations.
        txns.forEach(t -> canonicalByAlias.put(String.valueOf(t.getId()), t.getTxnRef()));
        alerts.forEach(a -> canonicalByAlias.putIfAbsent(
                String.valueOf(a.getId()), alertEvidenceId(a)));

        for (JsonNode observation : normalized.path("confirmedObservations")) {
            if (!observation.isObject() || !observation.path("evidenceIds").isArray()) continue;
            Set<String> seen = new LinkedHashSet<>();
            ArrayNode evidenceIds = Jsons.arr();
            for (JsonNode idNode : observation.path("evidenceIds")) {
                if (!idNode.isTextual()) continue;
                String supplied = idNode.asText().trim();
                if (supplied.isEmpty()) continue;
                String canonical = canonicalByAlias.getOrDefault(supplied, supplied);
                if (seen.add(canonical)) evidenceIds.add(canonical);
            }
            ((ObjectNode) observation).set("evidenceIds", evidenceIds);
        }
        return normalized;
    }

    private static String alertEvidenceId(AlertEntity alert) {
        return alert.getRuleCode() + "-ALERT-" + alert.getId();
    }

    private ObjectNode buildTemplate(
            CaseEntity c, CustomerEntity customer, List<AlertEntity> alerts, List<TransactionEntity> txns) {
        // Evidence must cite the business reference the analyst sees on the timeline (TXN-001234),
        // not the database primary key, otherwise the citation cannot be checked.
        Map<Long, String> refById = new HashMap<>();
        txns.forEach(t -> refById.put(t.getId(), t.getTxnRef()));
        ObjectNode out = Jsons.obj();
        String narrative = String.format(
                "%s (%s) shows unusual payment activity in the review window with a priority score of %d (%s). "
                        + "Inbound funds were followed by rapid outbound transfers across multiple counterparties. "
                        + "This may constitute layering. "
                        + "Investigators should corroborate commercial purpose before any escalation decision.",
                customer.getName(), customer.getCustomerRef(), c.getPriorityScore(), c.getPriorityBand());
        out.put("narrative", narrative);

        ArrayNode confirmed = Jsons.arr();
        for (AlertEntity a : alerts) {
            ObjectNode obs = Jsons.obj();
            obs.put("statement", a.getRuleName() + " contributed " + a.getPoints() + " points (strength "
                    + a.getStrength() + ").");
            ArrayNode ids = Jsons.arr();
            ids.add(alertEvidenceId(a));
            if (a.getEvidenceSnapshot() != null && a.getEvidenceSnapshot().has("transactionIds")) {
                for (JsonNode id : a.getEvidenceSnapshot().get("transactionIds")) {
                    String ref = refById.get(id.asLong());
                    if (ref != null) ids.add(ref);
                }
            }
            obs.set("evidenceIds", ids);
            confirmed.add(obs);
        }
        out.set("confirmedObservations", confirmed);

        ArrayNode questions = Jsons.arr();
        questions.add("What is the commercial rationale for the inbound credits?");
        questions.add("Are the new counterparties known to the relationship manager?");
        questions.add("Is there supporting documentation for the higher-risk jurisdiction transfer?");
        out.set("unexplainedQuestions", questions);

        ArrayNode checks = Jsons.arr();
        checks.add("Request invoice/contract pack for the review-window transfers");
        checks.add("Confirm beneficial ownership and source of funds with KYC");
        checks.add("Check prior closed cases and open sanctions hits for the same customer");
        out.set("suggestedNextChecks", checks);
        return out;
    }

    private String callOllama(ObjectNode input) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        long timeout = appProperties.getOllama().getTimeoutMs();
        rf.setConnectTimeout(Duration.ofMillis(Math.min(timeout, 5000)));
        rf.setReadTimeout(Duration.ofMillis(timeout));
        RestClient client = restClientBuilder.requestFactory(rf)
                .baseUrl(appProperties.getOllama().getBaseUrl())
                .build();

        String prompt = """
                You are an AML investigation assistant. Output ONLY valid JSON with keys:
                narrative, confirmedObservations, unexplainedQuestions, suggestedNextChecks.
                Hard constraints — you MUST NEVER: modify risk scores; auto-close or escalate cases;
                modify CRR; declare money laundering / guilt; auto-submit SAR; modify rules.
                Use cautious language: unusual activity, requires further investigation.
                Keep narrative to 3-4 sentences.
                Evidence IDs must be copied verbatim only from an alert evidenceId or a transaction
                txnRef value. Never use a database ID, ruleCode by itself, or invent an identifier.
                confirmedObservations must be an array of objects. Every object must contain a
                statement string and an evidenceIds array of strings. The other two list fields
                must be arrays of strings.
                Input evidence:
                %s
                """.formatted(Jsons.pretty(input));

        Map<String, Object> body = new HashMap<>();
        body.put("model", appProperties.getOllama().getModel());
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("format", buildDraftSchema());
        // qwen3:4b is a thinking-capable model. Disabling hidden reasoning keeps the
        // response in the standard `response` field and reduces demo latency.
        body.put("think", false);
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.2);
        body.put("options", options);

        JsonNode resp = client.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return resp == null ? null : resp.path("response").asText(null);
    }

    private ObjectNode buildDraftSchema() {
        ObjectNode schema = Jsons.obj();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");

        properties.putObject("narrative").put("type", "string");

        ObjectNode observations = properties.putObject("confirmedObservations");
        observations.put("type", "array");
        ObjectNode observation = observations.putObject("items");
        observation.put("type", "object");
        observation.put("additionalProperties", false);
        ObjectNode observationProperties = observation.putObject("properties");
        observationProperties.putObject("statement").put("type", "string");
        ObjectNode evidenceIds = observationProperties.putObject("evidenceIds");
        evidenceIds.put("type", "array");
        evidenceIds.putObject("items").put("type", "string");
        observation.putArray("required").add("statement").add("evidenceIds");

        ObjectNode questions = properties.putObject("unexplainedQuestions");
        questions.put("type", "array");
        questions.putObject("items").put("type", "string");

        ObjectNode checks = properties.putObject("suggestedNextChecks");
        checks.put("type", "array");
        checks.putObject("items").put("type", "string");

        schema.putArray("required")
                .add("narrative")
                .add("confirmedObservations")
                .add("unexplainedQuestions")
                .add("suggestedNextChecks");
        return schema;
    }

    private static String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }

    @Transactional
    public AiDraftEntity accept(Long draftId, AiDraftAcceptRequest req) {
        if (req == null || req.analystFinalText() == null || req.analystFinalText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "analystFinalText is required");
        }
        AiDraftEntity draft = aiDraftRepository.findById(draftId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found"));
        draft.setAnalystFinalText(req.analystFinalText());
        draft.setEditedBy(appProperties.getActor());
        draft.setEditedAt(Instant.now());
        draft.setStatus("ACCEPTED");
        draft = aiDraftRepository.save(draft);
        auditService.record("AI_DRAFT_EDITED_AND_ACCEPTED", "AI_DRAFT", draft.getId(),
                Map.of("caseId", draft.getCaseId()));
        return draft;
    }
}
