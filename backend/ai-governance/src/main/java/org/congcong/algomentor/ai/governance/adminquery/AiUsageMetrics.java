package org.congcong.algomentor.ai.governance.adminquery;

import java.math.BigDecimal;

/** 调用、Token、已定价成本和未定价部分的共同统计字段。 */
public record AiUsageMetrics(
    long modelCallCount,
    long inputTokens,
    long cachedTokens,
    long outputTokens,
    long reasoningTokens,
    long totalTokens,
    long pricedCallCount,
    long pricedTokenCount,
    BigDecimal estimatedCostUsd,
    long unpricedCallCount,
    long unpricedTokenCount
) {

  public static AiUsageMetrics empty() {
    return new AiUsageMetrics(0, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO.setScale(8), 0, 0);
  }
}
