package org.congcong.algomentor.ai.governance.repository.mybatis.model;

import java.math.BigDecimal;
import java.time.Instant;
import org.congcong.algomentor.ai.governance.pricing.AiModelPrice;

public record AiModelPriceRow(
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

  public AiModelPrice toDomain() {
    return new AiModelPrice(
        id,
        provider,
        model,
        currency,
        inputPricePerMillion,
        cachedInputPricePerMillion,
        outputPricePerMillion,
        costMultiplier,
        enabled,
        updatedBy,
        createdAt,
        updatedAt);
  }

  public static AiModelPriceRow fromDomain(AiModelPrice price) {
    return new AiModelPriceRow(
        price.id(),
        price.provider(),
        price.model(),
        price.currency(),
        price.inputPricePerMillion(),
        price.cachedInputPricePerMillion(),
        price.outputPricePerMillion(),
        price.costMultiplier(),
        price.enabled(),
        price.updatedBy(),
        price.createdAt(),
        price.updatedAt());
  }
}
