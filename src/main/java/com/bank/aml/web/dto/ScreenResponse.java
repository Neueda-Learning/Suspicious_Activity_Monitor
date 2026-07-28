package com.bank.aml.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public record ScreenResponse(
        String outcome,
        BigDecimal similarity,
        Long matchedEntryId,
        JsonNode measures,
        JsonNode matchDetails) {}
