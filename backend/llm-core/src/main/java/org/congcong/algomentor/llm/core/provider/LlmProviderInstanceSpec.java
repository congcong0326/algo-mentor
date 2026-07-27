package org.congcong.algomentor.llm.core.provider;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 创建某一 provider instance 版本 Client 所需的低层配置快照。 */
public record LlmProviderInstanceSpec(
    long providerInstanceId,
    LlmProviderType providerType,
    JsonNode config,
    Instant updatedAt
) {

  public LlmProviderInstanceSpec {
    if (providerInstanceId < 1) {
      throw new IllegalArgumentException("providerInstanceId must be positive");
    }
    if (providerType == null) {
      throw new IllegalArgumentException("providerType must not be null");
    }
    if (config == null || !config.isObject()) {
      throw new IllegalArgumentException("provider config must be a JSON object");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("provider updatedAt must not be null");
    }
    config = config.deepCopy();
  }
}
