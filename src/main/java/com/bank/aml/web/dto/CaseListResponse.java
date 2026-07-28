package com.bank.aml.web.dto;

import java.util.List;

public record CaseListResponse(Totals totals, List<CaseSummaryDto> items) {
    public record Totals(long alerts, long cases, long assignedToMe, long overdue) {}
}
