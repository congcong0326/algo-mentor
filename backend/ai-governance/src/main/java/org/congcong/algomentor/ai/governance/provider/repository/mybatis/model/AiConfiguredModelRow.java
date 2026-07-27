package org.congcong.algomentor.ai.governance.provider.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.ai.governance.provider.model.AiConfiguredModel;

/** MyBatis configured model row. */
public record AiConfiguredModelRow(
    Long id,
    long providerInstanceId,
    String displayName,
    String modelId,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {

  public AiConfiguredModel toDomain() {
    return new AiConfiguredModel(
        id, providerInstanceId, displayName, modelId, enabled, createdAt, updatedAt);
  }

  public static AiConfiguredModelRow fromDomain(AiConfiguredModel model) {
    return new AiConfiguredModelRow(
        model.id(),
        model.providerInstanceId(),
        model.displayName(),
        model.upstreamModelId(),
        model.enabled(),
        model.createdAt(),
        model.updatedAt());
  }
}
