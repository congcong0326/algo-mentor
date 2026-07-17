package org.congcong.algomentor.ai.governance.adminquery;

/** 接近或达到当天请求额度的用户摘要。 */
public record AiQuotaRiskUser(
    long userId,
    String email,
    String displayName,
    long requestCount,
    int effectiveDailyRequestLimit,
    int usagePercent,
    boolean atLimit,
    boolean effectiveAiEnabled
) {
}
