package org.congcong.algomentor.llm.core.provider;

import java.util.Locale;

/** 代码注册的 provider 协议类型，不代表某一个管理员配置实例。 */
public record LlmProviderType(String value) {

  public LlmProviderType {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("LLM provider type must not be blank");
    }
    value = value.trim().toLowerCase(Locale.ROOT);
  }

  public static LlmProviderType of(String value) {
    return new LlmProviderType(value);
  }
}
