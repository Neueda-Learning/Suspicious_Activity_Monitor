package com.bank.aml.web;

import com.bank.aml.service.StatsService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StatsController {
    private final StatsService statsService;

    @GetMapping("/api/stats/rules")
    public Map<String, Object> rules() {
        return statsService.ruleStats();
    }
}
