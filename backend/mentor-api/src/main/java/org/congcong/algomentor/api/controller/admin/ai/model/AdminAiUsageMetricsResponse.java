package org.congcong.algomentor.api.controller.admin.ai.model;

/** 成本使用 decimal string，Token 和调用数使用整数。 */
public record AdminAiUsageMetricsResponse(
    long modelCallCount,
    long inputTokens,
    long cachedTokens,
    long outputTokens,
    long reasoningTokens,
    long totalTokens,
    long pricedCallCount,
    long pricedTokenCount,
    String estimatedCostUsd,
    long unpricedCallCount,
    long unpricedTokenCount
) {
}
