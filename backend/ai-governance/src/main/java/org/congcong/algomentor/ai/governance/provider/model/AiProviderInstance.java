package org.congcong.algomentor.ai.governance.provider.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Locale;

/** 管理员维护的一套真实 AI 提供商连接配置。 */
public record AiProviderInstance(
    Long id,
    String name,
    String providerType,
    boolean enabled,
    JsonNode config,
    Instant createdAt,
    Instant updatedAt
) {

  public AiProviderInstance {
    if (id != null && id < 1) {
      throw new IllegalArgumentException("provider instance id must be positive when present");
    }
    name = requiredText(name, "provider instance name", 120);
    providerType = requiredText(providerType, "provider type", 32).toLowerCase(Locale.ROOT);
    if (config == null || !config.isObject()) {
      throw new IllegalArgumentException("provider config must be a JSON object");
    }
    config = config.deepCopy();
    if (createdAt != null && updatedAt != null && updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("provider instance updatedAt must not precede createdAt");
    }
  }

  private static String requiredText(String value, String name, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(name + " exceeds max length " + maxLength);
    }
    return normalized;
  }
}
