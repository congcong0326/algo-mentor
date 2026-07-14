package org.congcong.algomentor.ai.governance.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.junit.jupiter.api.Test;

class AiCostCalculatorTest {

  private final AiCostCalculator calculator = new AiCostCalculator();

  @Test
  void calculatesUncachedCachedAndOutputTokensWithMultiplier() {
    AiCostEstimate estimate = calculator.estimate(
        new AiUsage(1_000_000, 100_000, 200_000, 50_000, 1_100_000),
        price("2.00000000", "0.50000000", "4.00000000", "1.500000"));

    assertThat(estimate.uncachedInputTokens()).isEqualTo(800_000);
    assertThat(estimate.estimatedCostUsd()).isEqualByComparingTo("3.15000000");
  }

  @Test
  void neverCreatesNegativeUncachedInputCostOrDoubleChargesReasoningTokens() {
    AiCostEstimate estimate = calculator.estimate(
        new AiUsage(100, 0, 200, 999, 200),
        price("10.00000000", "1.00000000", "3.00000000", "1.000000"));

    assertThat(estimate.uncachedInputTokens()).isZero();
    assertThat(estimate.estimatedCostUsd()).isEqualByComparingTo("0.00020000");
  }

  private static AiModelPrice price(String input, String cachedInput, String output, String multiplier) {
    return new AiModelPrice(
        1L,
        "OpenAI",
        "gpt-test",
        "USD",
        new BigDecimal(input),
        new BigDecimal(cachedInput),
        new BigDecimal(output),
        new BigDecimal(multiplier),
        true,
        1L,
        Instant.parse("2026-07-14T00:00:00Z"),
        Instant.parse("2026-07-14T00:00:00Z"));
  }
}
