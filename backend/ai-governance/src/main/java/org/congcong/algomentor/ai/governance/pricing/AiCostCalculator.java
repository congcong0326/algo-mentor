package org.congcong.algomentor.ai.governance.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.congcong.algomentor.ai.governance.model.AiUsage;

/** 统一维护当前价格下的 AI 调用成本公式。 */
public class AiCostCalculator {

  public static final int COST_SCALE = 8;
  private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);

  public AiCostEstimate estimate(AiUsage usage, AiModelPrice price) {
    AiUsage safeUsage = usage == null ? AiUsage.zero() : usage;
    long uncachedInputTokens = Math.max(safeUsage.inputTokens() - safeUsage.cachedTokens(), 0L);
    BigDecimal total = BigDecimal.valueOf(uncachedInputTokens)
        .multiply(price.inputPricePerMillion())
        .add(BigDecimal.valueOf(safeUsage.cachedTokens()).multiply(price.cachedInputPricePerMillion()))
        .add(BigDecimal.valueOf(safeUsage.outputTokens()).multiply(price.outputPricePerMillion()))
        .divide(ONE_MILLION)
        .multiply(price.costMultiplier())
        .setScale(COST_SCALE, RoundingMode.HALF_UP);
    return new AiCostEstimate(total, uncachedInputTokens, safeUsage.cachedTokens(), safeUsage.outputTokens());
  }
}
