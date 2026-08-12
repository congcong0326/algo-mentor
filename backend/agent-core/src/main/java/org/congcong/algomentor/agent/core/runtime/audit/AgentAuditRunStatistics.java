package org.congcong.algomentor.agent.core.runtime.audit;

/** 当前筛选范围内的低敏 run 聚合指标，不包含请求正文或工具结果。 */
public record AgentAuditRunStatistics(
    long runCount,
    long overBudgetRunCount,
    long compactionRunCount,
    long usageReportedRunCount,
    Long inputTokens,
    Long cachedTokens
) {

  public AgentAuditRunStatistics {
    if (runCount < 0 || overBudgetRunCount < 0 || compactionRunCount < 0 || usageReportedRunCount < 0) {
      throw new IllegalArgumentException("Audit statistics count must not be negative");
    }
  }

  public Double overBudgetRate() {
    return ratio(overBudgetRunCount, runCount);
  }

  public Double compactionRate() {
    return ratio(compactionRunCount, runCount);
  }

  public Double cacheRatio() {
    return inputTokens == null || inputTokens <= 0 || cachedTokens == null
        ? null
        : (double) cachedTokens / inputTokens;
  }

  private Double ratio(long numerator, long denominator) {
    return denominator == 0 ? null : (double) numerator / denominator;
  }
}
