package com.bank.aml.service;

import com.bank.aml.domain.TransactionEntity;
import com.bank.aml.repo.TransactionRepository;
import com.bank.aml.service.rules.RuleHit;
import com.bank.aml.util.Jsons;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuleEngineService {
    private final TransactionRepository transactionRepository;
    private final HighRiskJurisdictionService highRiskJurisdictionService;

    public List<RuleHit> evaluate(Long customerId, Instant asOf) {
        Instant windowStart = asOf.minus(24, ChronoUnit.HOURS);
        Instant baselineStart = asOf.minus(90, ChronoUnit.DAYS);
        List<TransactionEntity> windowTxns =
                transactionRepository.findByCustomerIdAndExecutedAtBetween(customerId, windowStart, asOf);
        List<TransactionEntity> baselineTxns =
                transactionRepository.findByCustomerIdAndExecutedAtBetween(customerId, baselineStart, windowStart);

        List<RuleHit> hits = new ArrayList<>();
        evaluateR1(windowTxns, baselineTxns, windowStart, asOf).ifPresent(hits::add);
        evaluateR2(windowTxns, windowStart, asOf).ifPresent(hits::add);
        evaluateR3(windowTxns, baselineTxns, windowStart, asOf).ifPresent(hits::add);
        evaluateR4(windowTxns, windowStart, asOf).ifPresent(hits::add);
        return hits;
    }

    private java.util.Optional<RuleHit> evaluateR1(
            List<TransactionEntity> window, List<TransactionEntity> baseline, Instant ws, Instant we) {
        BigDecimal inbound = sumGbp(window, "INBOUND");
        Map<String, BigDecimal> daily = new HashMap<>();
        for (TransactionEntity t : baseline) {
            if (!"INBOUND".equals(t.getDirection())) continue;
            String day = t.getExecutedAt().toString().substring(0, 10);
            daily.merge(day, t.getAmountGbp(), BigDecimal::add);
        }
        if (daily.isEmpty()) return java.util.Optional.empty();
        List<BigDecimal> days = new ArrayList<>(daily.values());
        days.sort(Comparator.naturalOrder());
        BigDecimal median = days.get(days.size() / 2);
        if (median.compareTo(BigDecimal.ZERO) <= 0) return java.util.Optional.empty();
        BigDecimal ratio = inbound.divide(median, 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("2.0")) < 0) return java.util.Optional.empty();
        BigDecimal strength = clamp(ratio.subtract(new BigDecimal("2")).divide(new BigDecimal("8"), 6, RoundingMode.HALF_UP));
        int points = (int) Math.round(35 * strength.doubleValue());
        ObjectNode evidence = Jsons.obj();
        evidence.put("windowInboundGbp", inbound);
        evidence.put("baselineMedianGbp", median);
        evidence.put("baselineActiveDays", days.size());
        evidence.put("baselineInboundTxnCount",
                (int) baseline.stream().filter(t -> "INBOUND".equals(t.getDirection())).count());
        evidence.put("baselineDays", 90);
        evidence.put("ratio", ratio);
        evidence.set("transactionIds", txnIds(window.stream().filter(t -> "INBOUND".equals(t.getDirection())).toList()));
        ObjectNode params = Jsons.obj();
        params.put("weight", 35);
        params.put("thresholdRatio", 2.0);
        return java.util.Optional.of(new RuleHit("R1", "Amount deviation", 35, strength, points, params, evidence, ws, we));
    }

    private java.util.Optional<RuleHit> evaluateR2(List<TransactionEntity> window, Instant ws, Instant we) {
        List<TransactionEntity> inbounds = window.stream()
                .filter(t -> "INBOUND".equals(t.getDirection()))
                .sorted(Comparator.comparing(TransactionEntity::getExecutedAt))
                .toList();
        if (inbounds.isEmpty()) return java.util.Optional.empty();
        Instant firstIn = inbounds.get(0).getExecutedAt();
        BigDecimal inboundSum = sumGbp(inbounds, "INBOUND");
        List<TransactionEntity> outbounds = window.stream()
                .filter(t -> "OUTBOUND".equals(t.getDirection()))
                .filter(t -> !t.getExecutedAt().isBefore(firstIn))
                .filter(t -> Duration.between(firstIn, t.getExecutedAt()).toHours() <= 24)
                .toList();
        if (outbounds.isEmpty()) return java.util.Optional.empty();
        BigDecimal outboundSum = sumGbp(outbounds, "OUTBOUND");
        if (inboundSum.compareTo(BigDecimal.ZERO) <= 0) return java.util.Optional.empty();
        BigDecimal dispersalRatio = outboundSum.divide(inboundSum, 6, RoundingMode.HALF_UP);
        Set<String> cps = outbounds.stream()
                .map(t -> t.getCounterpartyRef() != null ? t.getCounterpartyRef() : t.getCounterpartyName())
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());
        if (dispersalRatio.compareTo(new BigDecimal("0.60")) < 0 || cps.size() < 3) {
            return java.util.Optional.empty();
        }
        Instant lastOut = outbounds.stream().map(TransactionEntity::getExecutedAt).max(Instant::compareTo).orElse(firstIn);
        double hours = Math.max(0, Duration.between(firstIn, lastOut).toMinutes() / 60.0);
        BigDecimal timeFactor = clamp(BigDecimal.valueOf((24 - hours) / 24.0));
        BigDecimal strength = clamp(dispersalRatio).multiply(timeFactor).setScale(3, RoundingMode.HALF_UP);
        int points = (int) Math.round(30 * strength.doubleValue());
        ObjectNode evidence = Jsons.obj();
        evidence.put("inboundGbp", inboundSum);
        evidence.put("outboundGbp", outboundSum);
        evidence.put("dispersalRatio", dispersalRatio);
        evidence.put("counterpartyCount", cps.size());
        evidence.put("hoursToDisperse", hours);
        evidence.set("transactionIds", txnIds(outbounds));
        ObjectNode params = Jsons.obj();
        params.put("weight", 30);
        params.put("minDispersalRatio", 0.60);
        params.put("minCounterparties", 3);
        return java.util.Optional.of(new RuleHit("R2", "Rapid dispersal", 30, strength, points, params, evidence, ws, we));
    }

    private java.util.Optional<RuleHit> evaluateR3(
            List<TransactionEntity> window, List<TransactionEntity> baseline, Instant ws, Instant we) {
        Set<String> known = new HashSet<>();
        for (TransactionEntity t : baseline) {
            if (t.getCounterpartyRef() != null) known.add(t.getCounterpartyRef());
        }
        Set<String> news = new HashSet<>();
        List<Long> ids = new ArrayList<>();
        // Only outbound beneficiaries count: a new payer is not the same risk signal
        // as funds being pushed to counterparties the customer has never dealt with.
        for (TransactionEntity t : window) {
            if (!"OUTBOUND".equals(t.getDirection())) continue;
            if (t.getCounterpartyRef() == null) continue;
            if (!known.contains(t.getCounterpartyRef())) {
                news.add(t.getCounterpartyRef());
                ids.add(t.getId());
            }
        }
        if (news.size() < 2) return java.util.Optional.empty();
        BigDecimal strength = clamp(BigDecimal.valueOf(news.size() / 5.0));
        int points = (int) Math.round(20 * strength.doubleValue());
        ObjectNode evidence = Jsons.obj();
        evidence.put("newCount", news.size());
        evidence.putPOJO("newCounterparties", news);
        ArrayNode arr = Jsons.arr();
        ids.forEach(arr::add);
        evidence.set("transactionIds", arr);
        ObjectNode params = Jsons.obj();
        params.put("weight", 20);
        params.put("minNewCounterparties", 2);
        return java.util.Optional.of(new RuleHit("R3", "New counterparties", 20, strength, points, params, evidence, ws, we));
    }

    private java.util.Optional<RuleHit> evaluateR4(List<TransactionEntity> window, Instant ws, Instant we) {
        List<TransactionEntity> outs = window.stream().filter(t -> "OUTBOUND".equals(t.getDirection())).toList();
        BigDecimal totalOut = sumGbp(outs, "OUTBOUND");
        if (totalOut.compareTo(BigDecimal.ZERO) <= 0) return java.util.Optional.empty();
        List<TransactionEntity> highRisk = outs.stream()
                .filter(t -> highRiskJurisdictionService.isHighRisk(t.getCounterpartyCountry()))
                .toList();
        if (highRisk.isEmpty()) return java.util.Optional.empty();
        BigDecimal valueHr = sumGbp(highRisk, "OUTBOUND");
        BigDecimal strength = clamp(valueHr.multiply(new BigDecimal("2")).divide(totalOut, 6, RoundingMode.HALF_UP));
        int points = (int) Math.round(15 * strength.doubleValue());
        ObjectNode evidence = Jsons.obj();
        evidence.put("valueToHighRisk", valueHr);
        evidence.put("totalOutbound", totalOut);
        evidence.put("highRiskTransferCount", highRisk.size());
        ArrayNode countries = Jsons.arr();
        highRisk.stream().map(TransactionEntity::getCounterpartyCountry).distinct().forEach(countries::add);
        evidence.set("highRiskCountries", countries);
        evidence.set("transactionIds", txnIds(highRisk));
        ObjectNode params = Jsons.obj();
        params.put("weight", 15);
        return java.util.Optional.of(new RuleHit("R4", "Higher-risk jurisdiction", 15, strength, points, params, evidence, ws, we));
    }

    private static BigDecimal sumGbp(List<TransactionEntity> txns, String direction) {
        return txns.stream()
                .filter(t -> direction.equals(t.getDirection()))
                .map(TransactionEntity::getAmountGbp)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static ArrayNode txnIds(List<TransactionEntity> txns) {
        ArrayNode arr = Jsons.arr();
        txns.forEach(t -> arr.add(t.getId()));
        return arr;
    }

    private static BigDecimal clamp(BigDecimal v) {
        if (v.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        if (v.compareTo(BigDecimal.ONE) > 0) return BigDecimal.ONE.setScale(3, RoundingMode.HALF_UP);
        return v.setScale(3, RoundingMode.HALF_UP);
    }
}
