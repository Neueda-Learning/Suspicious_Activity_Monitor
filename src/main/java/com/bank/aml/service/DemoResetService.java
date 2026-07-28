package com.bank.aml.service;

import com.bank.aml.seed.SeedDataRunner;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restores the demo to its opening state in one call.
 *
 * <p>Without this, the sanctions list-update beat can only ever be shown once: after the first
 * sync the v2 entries are already present, so a second run reports "0 added, 0 potential matches".
 * The same applies to disposed cases and accumulated AI drafts during a rehearsal.
 */
@Service
@Profile("demo")
@RequiredArgsConstructor
@Slf4j
public class DemoResetService {

    /** Truncated in dependency order; sanctions entries are reloaded from list-v1 afterwards. */
    private static final List<String> TABLES = List.of(
            "audit_event", "ai_draft", "sanctions_hit", "alert", "case_record",
            "txn", "sanctions_entry", "customer");

    private final EntityManager entityManager;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final SeedDataRunner seedDataRunner;

    @Transactional
    public Map<String, Object> reset() {
        for (String table : TABLES) {
            entityManager.createNativeQuery("TRUNCATE TABLE " + table + " RESTART IDENTITY CASCADE")
                    .executeUpdate();
        }
        entityManager.flush();
        entityManager.clear();

        sanctionsScreeningService.ensureV1Loaded();
        Map<String, Long> counts = seedDataRunner.seedAll();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "RESET");
        result.put("demoDay", SeedDataRunner.DEMO_DAY.toString());
        result.putAll(counts);
        log.info("Demo reset complete: {}", result);
        return result;
    }
}
