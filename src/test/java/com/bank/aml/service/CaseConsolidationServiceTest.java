package com.bank.aml.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.aml.config.AppProperties;
import com.bank.aml.domain.AlertEntity;
import com.bank.aml.domain.CaseEntity;
import com.bank.aml.repo.AlertRepository;
import com.bank.aml.repo.CaseRepository;
import com.bank.aml.service.rules.RuleHit;
import com.bank.aml.util.Jsons;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseConsolidationServiceTest {

    private static final Instant AS_OF = Instant.parse("2026-07-27T18:00:00Z");

    @Mock private RuleEngineService ruleEngineService;
    @Mock private AlertRepository alertRepository;
    @Mock private CaseRepository caseRepository;
    @Mock private AuditService auditService;

    private CaseConsolidationService service;
    private final AtomicLong nextId = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.setActor("sarah.chen");
        service = new CaseConsolidationService(
                ruleEngineService, alertRepository, caseRepository, auditService, props);
    }

    @Test
    @DisplayName("band thresholds: GREEN <40, AMBER 40-69, RED >=70")
    void bandThresholds() {
        assertThat(CaseConsolidationService.band(0)).isEqualTo("GREEN");
        assertThat(CaseConsolidationService.band(39)).isEqualTo("GREEN");
        assertThat(CaseConsolidationService.band(40)).isEqualTo("AMBER");
        assertThat(CaseConsolidationService.band(69)).isEqualTo("AMBER");
        assertThat(CaseConsolidationService.band(70)).isEqualTo("RED");
        assertThat(CaseConsolidationService.band(100)).isEqualTo("RED");
    }

    @Test
    @DisplayName("no rule hits means no case is opened")
    void emptyHitsOpenNothing() {
        when(ruleEngineService.evaluate(1L, AS_OF)).thenReturn(List.of());

        Optional<CaseEntity> result = service.evaluateAndConsolidate(1L, AS_OF);

        assertThat(result).isEmpty();
        verify(caseRepository, never()).save(any());
        verify(auditService, never()).recordAt(any(), anyString(), anyString(), anyLong(), anyMap());
    }

    @Test
    @DisplayName("first hits open an OPEN case with SLA, window and RED band from summed points")
    void firstHitsOpenRedCase() {
        when(ruleEngineService.evaluate(1L, AS_OF)).thenReturn(List.of(
                hit("R1", 35, "1.000", 35),
                hit("R2", 25, "1.000", 25),
                hit("R3", 20, "1.000", 20)));
        when(caseRepository.findByCustomerId(1L)).thenReturn(List.of());
        when(caseRepository.count()).thenReturn(0L);
        when(caseRepository.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0)));
        when(alertRepository.findByCaseId(anyLong())).thenReturn(List.of());
        when(alertRepository.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0)));

        CaseEntity opened = service.evaluateAndConsolidate(1L, AS_OF).orElseThrow();

        assertThat(opened.getStatus()).isEqualTo("OPEN");
        assertThat(opened.getPriorityScore()).isEqualTo(80);
        assertThat(opened.getPriorityBand()).isEqualTo("RED");
        assertThat(opened.getAssignedTo()).isEqualTo("sarah.chen");
        assertThat(opened.getOpenedAt()).isEqualTo(AS_OF);
        assertThat(opened.getSlaDueAt()).isEqualTo(AS_OF.plus(24, ChronoUnit.HOURS));
        assertThat(opened.getWindowStart()).isEqualTo(AS_OF.minus(24, ChronoUnit.HOURS));
        assertThat(opened.getCaseRef()).isEqualTo("CASE-2026-0001");
        verify(auditService).recordAt(eq(AS_OF), eq("CASE_OPENED"), eq("CASE"), eq(opened.getId()), anyMap());
    }

    @Test
    @DisplayName("a weaker re-hit for the same rule does not overwrite a stronger alert")
    void weakerAlertIsNotDowngraded() {
        CaseEntity open = new CaseEntity();
        open.setId(10L);
        open.setCustomerId(1L);
        open.setStatus("OPEN");
        open.setPriorityScore(20);
        open.setPriorityBand("GREEN");
        open.setCaseRef("CASE-2026-0007");
        open.setOpenedAt(AS_OF.minus(1, ChronoUnit.HOURS));

        AlertEntity existing = new AlertEntity();
        existing.setId(50L);
        existing.setCaseId(10L);
        existing.setCustomerId(1L);
        existing.setRuleCode("R1");
        existing.setRuleName("Volume spike");
        existing.setStrength(new BigDecimal("0.800"));
        existing.setPoints(28);

        when(ruleEngineService.evaluate(1L, AS_OF)).thenReturn(List.of(
                hit("R1", 35, "0.500", 17)));
        when(caseRepository.findByCustomerId(1L)).thenReturn(List.of(open));
        when(alertRepository.findByCaseId(10L)).thenReturn(List.of(existing));
        when(caseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CaseEntity result = service.evaluateAndConsolidate(1L, AS_OF).orElseThrow();

        verify(alertRepository, never()).save(any());
        assertThat(result.getPriorityScore()).isEqualTo(28);
        assertThat(result.getPriorityBand()).isEqualTo("GREEN");
    }

    private static RuleHit hit(String code, int weight, String strength, int points) {
        return new RuleHit(
                code, code + " name", weight, new BigDecimal(strength), points,
                Jsons.emptyObj(), Jsons.emptyObj(),
                AS_OF.minus(24, ChronoUnit.HOURS), AS_OF);
    }

    private <T> T assignId(T entity) {
        if (entity instanceof CaseEntity c && c.getId() == null) {
            c.setId(nextId.getAndIncrement());
        }
        if (entity instanceof AlertEntity a && a.getId() == null) {
            a.setId(nextId.getAndIncrement());
        }
        return entity;
    }
}
