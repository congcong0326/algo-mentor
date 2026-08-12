package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

/** 审计列表同一筛选范围内的 SQL 聚合结果。 */
public record AgentAuditRunStatisticsRow(
    long runCount,
    long overBudgetRunCount,
    long compactionRunCount,
    long usageReportedRunCount,
    Long inputTokens,
    Long cachedTokens
) {
}
