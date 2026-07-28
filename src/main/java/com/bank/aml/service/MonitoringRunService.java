package com.bank.aml.service;

import com.bank.aml.domain.CustomerEntity;
import com.bank.aml.repo.CaseRepository;
import com.bank.aml.repo.CustomerRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The nightly transaction monitoring sweep.
 *
 * <p>Monitoring screens the whole customer population, not a sample — a bank cannot choose not
 * to look at some of its customers. Evaluation is therefore exhaustive; alerting is not. Most
 * customers produce nothing, which is the entire point: the run exists to reduce thousands of
 * payments to the handful of cases an analyst can actually investigate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringRunService {

    private final CustomerRepository customerRepository;
    private final CaseRepository caseRepository;
    private final CaseConsolidationService caseConsolidationService;
    private final AuditService auditService;

    @Scheduled(cron = "${aml.monitoring.cron:0 0 2 * * *}")
    public void nightlyRun() {
        log.info("Nightly monitoring sweep: {}", run(Instant.now()));
    }

    /**
     * Evaluates every customer against the rule set as at {@code asOf}.
     *
     * <p>Customers already carrying an open case are skipped rather than re-alerted. Raising a
     * second alert for activity an analyst is already investigating adds queue noise without
     * adding information; suppressing it while the case is open is standard practice.
     */
    @Transactional
    public Map<String, Object> run(Instant asOf) {
        int evaluated = 0;
        int suppressed = 0;
        int casesRaised = 0;

        for (CustomerEntity c : customerRepository.findAll()) {
            if (caseRepository.existsByCustomerIdAndStatus(c.getId(), "OPEN")) {
                suppressed++;
                continue;
            }
            evaluated++;
            if (caseConsolidationService.evaluateAndConsolidate(c.getId(), asOf).isPresent()) {
                casesRaised++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asOf", asOf.toString());
        result.put("customersEvaluated", evaluated);
        result.put("suppressedOpenCase", suppressed);
        result.put("casesRaised", casesRaised);
        result.put("noActionRequired", evaluated - casesRaised);

        auditService.record("MONITORING_RUN_COMPLETED", "MONITORING_RUN", 0L, result);
        return result;
    }
}
