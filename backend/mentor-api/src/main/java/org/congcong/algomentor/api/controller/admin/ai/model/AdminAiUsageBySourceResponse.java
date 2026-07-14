package org.congcong.algomentor.api.controller.admin.ai.model;

public record AdminAiUsageBySourceResponse(
    String source,
    AdminAiUsageMetricsResponse metrics
) {
}
