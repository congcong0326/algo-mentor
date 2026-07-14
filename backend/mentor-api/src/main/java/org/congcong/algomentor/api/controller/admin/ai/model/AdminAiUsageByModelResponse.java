package org.congcong.algomentor.api.controller.admin.ai.model;

public record AdminAiUsageByModelResponse(
    String provider,
    String model,
    boolean priced,
    AdminAiUsageMetricsResponse metrics
) {
}
