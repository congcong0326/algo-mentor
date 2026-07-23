package org.congcong.algomentor.policy.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** 供 HTTP 返回的有效策略，保留数据库语义的原始 JSON。 */
public record EffectiveGenericPolicy(
    long policyId,
    String typeCode,
    int priority,
    JsonNode content
) {

  public EffectiveGenericPolicy {
    if (policyId < 1 || priority < 1) {
      throw new IllegalArgumentException("effective policy id and priority must be positive");
    }
    typeCode = Objects.requireNonNull(typeCode, "typeCode must not be null");
    content = Objects.requireNonNull(content, "content must not be null").deepCopy();
  }

  @Override
  public JsonNode content() {
    return content.deepCopy();
  }
}
