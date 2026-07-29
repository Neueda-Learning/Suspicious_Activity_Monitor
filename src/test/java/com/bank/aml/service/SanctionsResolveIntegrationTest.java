package com.bank.aml.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.aml.domain.SanctionsHitEntity;
import com.bank.aml.domain.TransactionEntity;
import com.bank.aml.repo.SanctionsHitRepository;
import com.bank.aml.repo.TransactionRepository;
import com.bank.aml.web.dto.PaymentRequest;
import com.bank.aml.web.dto.PaymentResponse;
import com.bank.aml.web.dto.ResolveHitRequest;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolve is what turns a held payment into either an executed false-match release or a
 * permanent restriction. Covered here at the service boundary the API and demo both use.
 */
@SpringBootTest
@ActiveProfiles("demo")
class SanctionsResolveIntegrationTest {

    @Autowired private DemoResetService demoResetService;
    @Autowired private SanctionsScreeningService sanctionsScreeningService;
    @Autowired private SanctionsHitRepository sanctionsHitRepository;
    @Autowired private TransactionRepository transactionRepository;

    @BeforeEach
    void reset() {
        demoResetService.reset();
    }

    @Test
    @DisplayName("FALSE_MATCH releases the held payment and stamps executedAt")
    void falseMatchReleasesPayment() {
        PaymentResponse held = holdVladmir();

        SanctionsHitEntity resolved = sanctionsScreeningService.resolve(
                held.sanctionsHitId(),
                new ResolveHitRequest("FALSE_MATCH", "Common name; DOB confirmed with relationship manager"));

        assertThat(resolved.getStatus()).isEqualTo("FALSE_MATCH");
        assertThat(resolved.getResolutionRationale()).contains("Common name");
        TransactionEntity txn = transactionRepository.findById(held.paymentTxnId()).orElseThrow();
        assertThat(txn.getStatus()).isEqualTo("RELEASED_FALSE_MATCH");
        assertThat(txn.getExecutedAt()).isNotNull();
    }

    @Test
    @DisplayName("TARGET_MATCH restricts the payment and never executes it")
    void targetMatchRestrictsPayment() {
        PaymentResponse held = holdVladmir();

        SanctionsHitEntity resolved = sanctionsScreeningService.resolve(
                held.sanctionsHitId(),
                new ResolveHitRequest("TARGET_MATCH", "Confirmed against list identifiers"));

        assertThat(resolved.getStatus()).isEqualTo("TARGET_MATCH");
        TransactionEntity txn = transactionRepository.findById(held.paymentTxnId()).orElseThrow();
        assertThat(txn.getStatus()).isEqualTo("RESTRICTED_TARGET_MATCH");
        assertThat(txn.getExecutedAt()).isNull();
    }

    @Test
    @DisplayName("a second resolve on the same hit is rejected with 409")
    void secondResolveConflicts() {
        PaymentResponse held = holdVladmir();
        sanctionsScreeningService.resolve(
                held.sanctionsHitId(),
                new ResolveHitRequest("FALSE_MATCH", "First decision wins"));

        assertThatThrownBy(() -> sanctionsScreeningService.resolve(
                held.sanctionsHitId(),
                new ResolveHitRequest("TARGET_MATCH", "Too late")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(409);
                    assertThat(rse.getReason()).contains("already resolved");
                });

        SanctionsHitEntity hit = sanctionsHitRepository.findById(held.sanctionsHitId()).orElseThrow();
        assertThat(hit.getStatus()).isEqualTo("FALSE_MATCH");
    }

    @Test
    @DisplayName("resolve requires a non-blank rationale")
    void blankRationaleIsRejected() {
        PaymentResponse held = holdVladmir();

        assertThatThrownBy(() -> sanctionsScreeningService.resolve(
                held.sanctionsHitId(),
                new ResolveHitRequest("FALSE_MATCH", "  ")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value())
                        .isEqualTo(400));
    }

    private PaymentResponse holdVladmir() {
        PaymentResponse res = sanctionsScreeningService.screenPayment(new PaymentRequest(
                "CUS-000002", "Vladmir Petrov", "RU",
                new BigDecimal("12500.00"), "GBP", "1971-03-02", "RU"));
        assertThat(res.status()).isEqualTo("HELD");
        assertThat(res.sanctionsHitId()).isNotNull();
        return res;
    }
}
