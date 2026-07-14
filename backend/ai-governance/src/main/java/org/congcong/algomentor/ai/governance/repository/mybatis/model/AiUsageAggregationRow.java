package org.congcong.algomentor.ai.governance.repository.mybatis.model;

import java.math.BigDecimal;

/** SQL 按展示维度和价格元组聚合后的中间行，成本仅在 service 层计算。 */
public record AiUsageAggregationRow(
    Long userId,
    String provider,
    String model,
    String purpose,
    String source,
    long modelCallCount,
    long inputTokens,
    long cachedTokens,
    long outputTokens,
    long reasoningTokens,
    long totalTokens,
    Long priceId,
    BigDecimal inputPricePerMillion,
    BigDecimal cachedInputPricePerMillion,
    BigDecimal outputPricePerMillion,
    BigDecimal costMultiplier
) {
}
