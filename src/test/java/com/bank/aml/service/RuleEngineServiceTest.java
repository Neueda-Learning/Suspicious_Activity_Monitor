package com.bank.aml.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.aml.domain.TransactionEntity;
import com.bank.aml.repo.TransactionRepository;
import com.bank.aml.service.rules.RuleHit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleEngineServiceTest {

    private static final Instant AS_OF = Instant.parse("2026-07-27T18:00:00Z");
    private static final Instant WINDOW_START = AS_OF.minus(24, ChronoUnit.HOURS);

    @Mock private TransactionRepository transactionRepository;
    @Mock private HighRiskJurisdictionService highRiskJurisdictionService;

    private RuleEngineService ruleEngine;
    private final List<TransactionEntity> window = new ArrayList<>();
    private final List<TransactionEntity> baseline = new ArrayList<>();
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        ruleEngine = new RuleEngineService(transactionRepository, highRiskJurisdictionService);
    }

    private void wireRepository() {
        when(transactionRepository.findByCustomerIdAndExecutedAtBetween(eq(1L), any(), any()))
                .thenAnswer(inv -> {
                    Instant from = inv.getArgument(1);
                    return from.equals(WINDOW_START) ? window : baseline;
                });
    }

    private TransactionEntity txn(
            List<TransactionEntity> bucket, String direction, String amount, String cpRef,
            String country, Instant when) {
        TransactionEntity t = new TransactionEntity();
        t.setId(nextId++);
        t.setTxnRef(String.format("TXN-%06d", t.getId()));
        t.setCustomerId(1L);
        t.setDirection(direction);
        t.setAmountGbp(new BigDecimal(amount));
        t.setCounterpartyRef(cpRef);
        t.setCounterpartyCountry(country);
        t.setExecutedAt(when);
        bucket.add(t);
        return t;
    }

    /** Ninety days of £10k daily credits, so the baseline median is well defined. */
    private void seedOrdinaryBaseline() {
        for (int day = 2; day < 60; day++) {
            txn(baseline, "INBOUND", "10000", "CP-KNOWN-" + (day % 3), "DE",
                    AS_OF.minus(day, ChronoUnit.DAYS));
        }
    }

    private Map<String, RuleHit> evaluate() {
        wireRepository();
        return ruleEngine.evaluate(1L, AS_OF).stream()
                .collect(Collectors.toMap(RuleHit::ruleCode, Function.identity()));
    }

    @Test
    @DisplayName("R3 counts new beneficiaries only, never new payers")
    void r3IgnoresInboundCounterparties() {
        seedOrdinaryBaseline();
        // Three payers never seen before send money in. They must not inflate the count.
        txn(window, "INBOUND", "30000", "CP-NEW-PAYER-1", "DE", WINDOW_START.plus(1, ChronoUnit.HOURS));
        txn(window, "INBOUND", "27000", "CP-NEW-PAYER-2", "FR", WINDOW_START.plus(1, ChronoUnit.HOURS));
        txn(window, "INBOUND", "27000", "CP-NEW-PAYER-3", "EE", WINDOW_START.plus(1, ChronoUnit.HOURS));
        // Two brand-new beneficiaries, plus one the customer has always paid.
        txn(window, "OUTBOUND", "20000", "CP-NEW-BEN-1", "US", WINDOW_START.plus(2, ChronoUnit.HOURS));
        txn(window, "OUTBOUND", "20000", "CP-NEW-BEN-2", "CY", WINDOW_START.plus(2, ChronoUnit.HOURS));
        txn(window, "OUTBOUND", "20000", "CP-KNOWN-1", "DE", WINDOW_START.plus(2, ChronoUnit.HOURS));

        RuleHit r3 = evaluate().get("R3");

        assertThat(r3).isNotNull();
        assertThat(r3.evidenceSnapshot().get("newCount").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("points are weight x strength, and each rule is capped at its own weight")
    void pointsFollowWeightTimesStrength() {
        seedOrdinaryBaseline();
        // 20x the baseline median is far past the saturation point, so R1 must peg at its weight.
        txn(window, "INBOUND", "200000", "CP-KNOWN-1", "DE", WINDOW_START.plus(1, ChronoUnit.HOURS));

        RuleHit r1 = evaluate().get("R1");

        assertThat(r1.strength()).isEqualByComparingTo("1.000");
        assertThat(r1.points()).isEqualTo(35);
    }

    @Test
    @DisplayName("R1 stays silent below the 2x trigger, so ordinary trading does not alert")
    void r1DoesNotFireWithinNormalRange() {
        seedOrdinaryBaseline();
        txn(window, "INBOUND", "15000", "CP-KNOWN-1", "DE", WINDOW_START.plus(1, ChronoUnit.HOURS));

        assertThat(evaluate()).doesNotContainKey("R1");
    }

    @Test
    @DisplayName("a sanctions signal never contributes to the AML score")
    void ruleEngineEmitsOnlyTheFourAmlRules() {
        seedOrdinaryBaseline();
        txn(window, "INBOUND", "84200", "CP-KNOWN-1", "DE", WINDOW_START.plus(1, ChronoUnit.HOURS));
        txn(window, "OUTBOUND", "26000", "CP-NEW-1", "NG", WINDOW_START.plus(2, ChronoUnit.HOURS));
        txn(window, "OUTBOUND", "26000", "CP-NEW-2", "US", WINDOW_START.plus(2, ChronoUnit.HOURS));
        txn(window, "OUTBOUND", "25000", "CP-NEW-3", "CY", WINDOW_START.plus(2, ChronoUnit.HOURS));
        when(highRiskJurisdictionService.isHighRisk("NG")).thenReturn(true);

        Map<String, RuleHit> hits = evaluate();

        assertThat(hits.keySet()).containsExactlyInAnyOrder("R1", "R2", "R3", "R4");
        assertThat(hits.values().stream().mapToInt(RuleHit::weight).sum()).isLessThanOrEqualTo(100);
    }
}
