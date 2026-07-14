package org.congcong.algomentor.api.controller.admin.ai.model;

public record AdminAiUsageByUserResponse(
    long userId,
    String email,
    String displayName,
    String accountStatus,
    AdminAiUsageMetricsResponse metrics,
    long todayEntryRequestCount,
    int effectiveDailyRequestLimit,
    boolean effectiveAiEnabled
) {
}
