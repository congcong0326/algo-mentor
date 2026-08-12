package org.congcong.algomentor.api.controller.admin.ai.model;

/** 当前审计列表筛选范围内的聚合指标。 */
public record AdminAiAuditRunStatisticsResponse(
    long runCount,
    long overBudgetRunCount,
    Double overBudgetRate,
    long compactionRunCount,
    Double compactionRate,
    long usageReportedRunCount,
    Long inputTokens,
    Long cachedTokens,
    Double cacheRatio
) {
}
