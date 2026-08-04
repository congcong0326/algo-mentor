package org.congcong.algomentor.llm.core.provider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/**
 * 仅在当前 Agent run 内传递的 provider 私有 continuation。
 *
 * <p>该对象不是通用 JSON DTO；payload 只能由匹配的 provider mapper 读取，禁止写入消息 metadata、
 * SSE、日志、trace 或持久化模型。</p>
 */
@JsonIgnoreType
public final class LlmProviderContinuation {

  private final LlmProviderType providerType;
  private final JsonNode payload;

  public LlmProviderContinuation(LlmProviderType providerType, JsonNode payload) {
    this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
    if (payload == null || !(payload.isObject() || payload.isArray())) {
      throw new IllegalArgumentException("provider continuation payload must be a non-null object or array");
    }
    this.payload = payload.deepCopy();
  }

  @JsonIgnore
  public LlmProviderType providerType() {
    return providerType;
  }

  @JsonIgnore
  public JsonNode payload() {
    return payload.deepCopy();
  }

  @Override
  public String toString() {
    return "LlmProviderContinuation[REDACTED]";
  }
}
