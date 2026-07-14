package org.congcong.algomentor.api.controller.admin.ai.model;

import java.time.LocalDate;

public record AdminAiUsageSummaryResponse(
    LocalDate from,
    LocalDate to,
    String quotaZone,
    long admittedEntryRequestCount,
    AdminAiUsageMetricsResponse metrics
) {
}
