package org.congcong.algomentor.ai.governance.provider.model;

import java.time.Instant;

/** 绑定到一个提供商实例、可被模型路由引用的上游模型。 */
public record AiConfiguredModel(
    Long id,
    long providerInstanceId,
    String displayName,
    String upstreamModelId,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {

  public AiConfiguredModel {
    if (id != null && id < 1) {
      throw new IllegalArgumentException("configured model id must be positive when present");
    }
    if (providerInstanceId < 1) {
      throw new IllegalArgumentException("providerInstanceId must be positive");
    }
    displayName = requiredText(displayName, "configured model displayName", 120);
    upstreamModelId = requiredText(upstreamModelId, "configured upstream model id", 160);
    if (createdAt != null && updatedAt != null && updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("configured model updatedAt must not precede createdAt");
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
