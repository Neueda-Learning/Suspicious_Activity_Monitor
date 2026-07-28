package com.bank.aml.service;

import com.bank.aml.domain.AlertEntity;
import com.bank.aml.domain.CaseEntity;
import com.bank.aml.repo.AlertRepository;
import com.bank.aml.repo.CaseRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatsService {
    private final AlertRepository alertRepository;
    private final CaseRepository caseRepository;

    public Map<String, Object> ruleStats() {
        Map<String, Long> byRule = new LinkedHashMap<>();
        for (String code : new String[]{"R1", "R2", "R3", "R4"}) byRule.put(code, 0L);
        for (AlertEntity a : alertRepository.findAll()) {
            byRule.merge(a.getRuleCode(), 1L, Long::sum);
        }
        Map<String, Long> byDisposition = new LinkedHashMap<>();
        byDisposition.put("OPEN", 0L);
        byDisposition.put("CLOSED_NFA", 0L);
        byDisposition.put("CLOSED_FALSE_POSITIVE", 0L);
        byDisposition.put("ESCALATED_INTERNAL", 0L);
        for (CaseEntity c : caseRepository.findAll()) {
            byDisposition.merge(c.getStatus(), 1L, Long::sum);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ruleTriggers", byRule);
        out.put("dispositionDistribution", byDisposition);
        return out;
    }
}
