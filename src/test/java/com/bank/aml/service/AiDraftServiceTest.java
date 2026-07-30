package com.bank.aml.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.aml.domain.AlertEntity;
import com.bank.aml.domain.CaseEntity;
import com.bank.aml.domain.CustomerEntity;
import com.bank.aml.domain.TransactionEntity;
import com.bank.aml.util.Jsons;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiDraftServiceTest {

    @Test
    @DisplayName("model input exposes business evidence references, not database IDs")
    void modelInputDoesNotExposeDatabaseIds() {
        CaseEntity caseRecord = new CaseEntity();
        caseRecord.setCaseRef("CASE-2026-0001");
        caseRecord.setPriorityScore(83);
        caseRecord.setPriorityBand("RED");

        CustomerEntity customer = new CustomerEntity();
        customer.setCustomerRef("CUS-000001");
        customer.setName("ACME Trading Ltd");
        customer.setCrr("MEDIUM");

        AlertEntity alert = alert(42L);
        TransactionEntity transaction = transaction(1719L, "TXN-002719");

        ObjectNode input =
                AiDraftService.buildModelInput(caseRecord, customer, List.of(alert), List.of(transaction));

        JsonNode alertInput = input.path("alerts").get(0);
        assertThat(alertInput.path("evidenceId").asText()).isEqualTo("R2-ALERT-42");
        assertThat(alertInput.has("id")).isFalse();
        assertThat(alertInput.path("evidence").has("transactionIds")).isFalse();
        assertThat(alertInput.path("evidence").path("transactionRefs"))
                .extracting(JsonNode::asText)
                .containsExactly("TXN-002719");

        JsonNode transactionInput = input.path("transactions").get(0);
        assertThat(transactionInput.path("txnRef").asText()).isEqualTo("TXN-002719");
        assertThat(transactionInput.has("id")).isFalse();
        assertThat(input.toString()).doesNotContain("1719");
    }

    @Test
    @DisplayName("legacy numeric citations are canonicalized and unknown citations remain unverified")
    void numericCitationsAreCanonicalized() {
        AlertEntity alert = alert(42L);
        TransactionEntity transaction = transaction(1719L, "TXN-002719");

        ObjectNode draft = Jsons.obj();
        draft.put("narrative", "Unusual activity requires further investigation.");
        ArrayNode observations = draft.putArray("confirmedObservations");
        ObjectNode observation = observations.addObject();
        observation.put("statement", "Rapid dispersal of GBP funds.");
        observation.putArray("evidenceIds")
                .add("42")
                .add("1719")
                .add("TXN-002719")
                .add("MADE-UP-REF");
        draft.putArray("unexplainedQuestions");
        draft.putArray("suggestedNextChecks");

        ObjectNode normalized =
                AiDraftService.normalizeDraftCitations(draft, List.of(alert), List.of(transaction));

        assertThat(normalized.path("confirmedObservations").get(0).path("evidenceIds"))
                .extracting(JsonNode::asText)
                .containsExactly("R2-ALERT-42", "TXN-002719", "MADE-UP-REF");
    }

    private static AlertEntity alert(long id) {
        AlertEntity alert = new AlertEntity();
        alert.setId(id);
        alert.setRuleCode("R2");
        alert.setRuleName("Rapid dispersal");
        alert.setPoints(27);
        alert.setStrength(new BigDecimal("0.900"));
        ObjectNode evidence = Jsons.obj();
        evidence.putArray("transactionIds").add(1719L);
        alert.setEvidenceSnapshot(evidence);
        return alert;
    }

    private static TransactionEntity transaction(long id, String txnRef) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setTxnRef(txnRef);
        transaction.setDirection("OUTBOUND");
        transaction.setAmountGbp(new BigDecimal("1000.00"));
        transaction.setCounterpartyName("Example Counterparty");
        transaction.setCounterpartyCountry("GB");
        transaction.setExecutedAt(Instant.parse("2026-07-27T10:57:00Z"));
        return transaction;
    }
}
