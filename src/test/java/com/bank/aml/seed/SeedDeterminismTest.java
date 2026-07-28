package com.bank.aml.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.aml.domain.AlertEntity;
import com.bank.aml.domain.CaseEntity;
import com.bank.aml.repo.AlertRepository;
import com.bank.aml.repo.CaseRepository;
import com.bank.aml.service.DemoResetService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * The demo is rehearsed several times before it is performed. If a reset produced a slightly
 * different data set each time, the numbers quoted on stage would not be the numbers on screen.
 */
@SpringBootTest
@ActiveProfiles("demo")
class SeedDeterminismTest {

    @Autowired private DemoResetService demoResetService;
    @Autowired private CaseRepository caseRepository;
    @Autowired private AlertRepository alertRepository;

    @Test
    @DisplayName("two consecutive resets produce an identical data set")
    void resetIsDeterministic() {
        Map<String, Object> first = demoResetService.reset();
        Map<String, Integer> firstScores = scoresByCaseRef();

        Map<String, Object> second = demoResetService.reset();
        Map<String, Integer> secondScores = scoresByCaseRef();

        assertThat(second).isEqualTo(first);
        assertThat(secondScores).isEqualTo(firstScores);
    }

    @Test
    @DisplayName("every case score equals the sum of its alert points")
    void scoresAreFullyTraceableToAlerts() {
        demoResetService.reset();

        for (CaseEntity c : caseRepository.findAll()) {
            int sum = alertRepository.findByCaseId(c.getId()).stream()
                    .mapToInt(AlertEntity::getPoints)
                    .sum();
            assertThat(c.getPriorityScore())
                    .as("case %s score must be explained by its alerts", c.getCaseRef())
                    .isEqualTo(sum);
        }
    }

    @Test
    @DisplayName("no rule contributes twice to the same case")
    void eachRuleAppearsAtMostOncePerCase() {
        demoResetService.reset();

        for (CaseEntity c : caseRepository.findAll()) {
            List<String> codes = alertRepository.findByCaseId(c.getId()).stream()
                    .map(AlertEntity::getRuleCode)
                    .toList();
            assertThat(codes).as("case %s", c.getCaseRef()).doesNotHaveDuplicates();
        }
    }

    private Map<String, Integer> scoresByCaseRef() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        caseRepository.findAll().stream()
                .sorted((a, b) -> a.getCaseRef().compareTo(b.getCaseRef()))
                .forEach(c -> scores.put(c.getCaseRef(), c.getPriorityScore()));
        return scores;
    }
}
