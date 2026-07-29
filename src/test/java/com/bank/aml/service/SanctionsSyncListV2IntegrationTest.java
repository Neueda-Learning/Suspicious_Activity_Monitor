package com.bank.aml.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.aml.domain.CustomerEntity;
import com.bank.aml.domain.SanctionsHitEntity;
import com.bank.aml.repo.CustomerRepository;
import com.bank.aml.repo.SanctionsEntryRepository;
import com.bank.aml.repo.SanctionsHitRepository;
import com.bank.aml.web.dto.SyncResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * list-v2 adds Northern Peak Holdings Ltd. After sync, the seeded customer of that name
 * must pick up a LIST_UPDATE_RESCREEN potential match.
 */
@SpringBootTest
@ActiveProfiles("demo")
class SanctionsSyncListV2IntegrationTest {

    @Autowired private DemoResetService demoResetService;
    @Autowired private SanctionsScreeningService sanctionsScreeningService;
    @Autowired private SanctionsEntryRepository sanctionsEntryRepository;
    @Autowired private SanctionsHitRepository sanctionsHitRepository;
    @Autowired private CustomerRepository customerRepository;

    @BeforeEach
    void reset() {
        demoResetService.reset();
    }

    @Test
    @DisplayName("syncListV2 imports new entries and rescreens Northern Peak into a potential hit")
    void syncAddsNorthernPeakHit() {
        assertThat(sanctionsEntryRepository.findBySourceUniqueId("OS-DEMO-DORMANT-HIT-001")).isEmpty();

        CustomerEntity northernPeak = customerRepository.findAll().stream()
                .filter(c -> "Northern Peak Holdings Ltd".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        assertThat(sanctionsHitRepository.findByCustomerId(northernPeak.getId())).isEmpty();

        SyncResponse sync = sanctionsScreeningService.syncListV2();

        assertThat(sync.added()).isGreaterThanOrEqualTo(1);
        assertThat(sync.customersRescreened()).isEqualTo((int) customerRepository.count());
        assertThat(sync.potentialMatchesCreated()).isGreaterThanOrEqualTo(1);
        assertThat(sanctionsEntryRepository.findBySourceUniqueId("OS-DEMO-DORMANT-HIT-001")).isPresent();

        List<SanctionsHitEntity> hits = sanctionsHitRepository.findByCustomerId(northernPeak.getId());
        assertThat(hits).anySatisfy(h -> {
            assertThat(h.getTriggerType()).isEqualTo("LIST_UPDATE_RESCREEN");
            assertThat(h.getStatus()).isEqualTo("POTENTIAL_MATCH");
            assertThat(h.getPaymentTxnId()).isNull();
            assertThat(h.getScreenedName()).isEqualTo("Northern Peak Holdings Ltd");
        });
    }

    @Test
    @DisplayName("a second sync does not duplicate an open potential match for the same entry")
    void secondSyncIsIdempotentForOpenHits() {
        sanctionsScreeningService.syncListV2();
        CustomerEntity northernPeak = customerRepository.findAll().stream()
                .filter(c -> "Northern Peak Holdings Ltd".equals(c.getName()))
                .findFirst()
                .orElseThrow();
        long hitsAfterFirst = sanctionsHitRepository.findByCustomerId(northernPeak.getId()).stream()
                .filter(h -> "LIST_UPDATE_RESCREEN".equals(h.getTriggerType()))
                .count();

        SyncResponse again = sanctionsScreeningService.syncListV2();

        long hitsAfterSecond = sanctionsHitRepository.findByCustomerId(northernPeak.getId()).stream()
                .filter(h -> "LIST_UPDATE_RESCREEN".equals(h.getTriggerType()))
                .count();
        assertThat(hitsAfterSecond).isEqualTo(hitsAfterFirst);
        assertThat(again.potentialMatchesCreated()).isZero();
    }
}
