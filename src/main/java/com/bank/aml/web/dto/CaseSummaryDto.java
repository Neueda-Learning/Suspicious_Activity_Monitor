package com.bank.aml.web.dto;

import java.time.Instant;
import java.util.List;

public record CaseSummaryDto(
        Long id,
        String caseRef,
        Long customerId,
        String customerRef,
        String customerName,
        Integer priorityScore,
        String priorityBand,
        String status,
        String assignedTo,
        Instant slaDueAt,
        Instant openedAt,
        boolean overdue,
        int alertCount,
        List<String> ruleCodes) {}
