package com.bank.aml.service.rules;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record RuleHit(
        String ruleCode,
        String ruleName,
        int weight,
        BigDecimal strength,
        int points,
        JsonNode ruleParamsSnapshot,
        JsonNode evidenceSnapshot,
        Instant windowStart,
        Instant windowEnd
) {}
