package org.congcong.algomentor.ai.governance.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/** 当前启用或停用的 provider/model 单价配置。 */
public record AiModelPrice(
    Long id,
    String provider,
    String model,
    String currency,
    BigDecimal inputPricePerMillion,
    BigDecimal cachedInputPricePerMillion,
    BigDecimal outputPricePerMillion,
    BigDecimal costMultiplier,
    boolean enabled,
    Long updatedBy,
    Instant createdAt,
    Instant updatedAt
) {

  public static final String CURRENCY_USD = "USD";

  public AiModelPrice {
    provider = normalizeProvider(provider);
    model = normalizeModel(model);
    currency = CURRENCY_USD;
    validatePrice(inputPricePerMillion, 8, "inputPricePerMillion", true);
    validatePrice(cachedInputPricePerMillion, 8, "cachedInputPricePerMillion", true);
    validatePrice(outputPricePerMillion, 8, "outputPricePerMillion", true);
    validatePrice(costMultiplier, 6, "costMultiplier", false);
  }

  public static String normalizeProvider(String provider) {
    if (provider == null || provider.isBlank() || provider.trim().length() > 80) {
      throw new IllegalArgumentException("provider must contain no more than 80 characters");
    }
    return provider.trim().toLowerCase(Locale.ROOT);
  }

  public static String normalizeModel(String model) {
    if (model == null || model.isBlank() || model.trim().length() > 160) {
      throw new IllegalArgumentException("model must contain no more than 160 characters");
    }
    return model.trim();
  }

  private static void validatePrice(BigDecimal value, int maxScale, String field, boolean zeroAllowed) {
    if (value == null || value.scale() > maxScale || value.signum() < 0 || (!zeroAllowed && value.signum() == 0)) {
      throw new IllegalArgumentException(field + " is invalid");
    }
  }
}
