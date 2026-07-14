package org.congcong.algomentor.ai.governance.pricing;

import java.math.BigDecimal;

/** 当前价格回算得到的金额和可计费 Token 摘要。 */
public record AiCostEstimate(
    BigDecimal estimatedCostUsd,
    long uncachedInputTokens,
    long cachedInputTokens,
    long outputTokens
) {
}
