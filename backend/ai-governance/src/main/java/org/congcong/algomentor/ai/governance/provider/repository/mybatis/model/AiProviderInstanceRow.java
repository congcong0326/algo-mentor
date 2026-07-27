package org.congcong.algomentor.ai.governance.provider.repository.mybatis.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.congcong.algomentor.ai.governance.provider.model.AiProviderInstance;

/** MyBatis provider instance row. */
public record AiProviderInstanceRow(
    Long id,
    String name,
    String providerType,
    boolean enabled,
    JsonNode config,
    Instant createdAt,
    Instant updatedAt
) {

  public AiProviderInstance toDomain() {
    return new AiProviderInstance(id, name, providerType, enabled, config, createdAt, updatedAt);
  }

  public static AiProviderInstanceRow fromDomain(AiProviderInstance instance) {
    return new AiProviderInstanceRow(
        instance.id(),
        instance.name(),
        instance.providerType(),
        instance.enabled(),
        instance.config(),
        instance.createdAt(),
        instance.updatedAt());
  }
}
