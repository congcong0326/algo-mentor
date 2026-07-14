package org.congcong.algomentor.ai.governance.adminquery;

/** 按用户聚合的调用、Token、成本与当前策略摘要。 */
public record AiUsageByUserRow(
    long userId,
    String email,
    String displayName,
    String accountStatus,
    AiUsageMetrics metrics,
    long todayEntryRequestCount,
    int effectiveDailyRequestLimit,
    boolean effectiveAiEnabled
) {
}
