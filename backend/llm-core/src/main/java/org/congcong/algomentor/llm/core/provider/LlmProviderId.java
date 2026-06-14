package org.congcong.algomentor.llm.core.provider;

/**
 * 用于已配置 LLM 提供商标识符的值对象。
 */
public record LlmProviderId(String value) {

  public LlmProviderId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("LLM provider id must not be blank");
    }
    value = value.trim();
  }

  public static LlmProviderId of(String value) {
    return new LlmProviderId(value);
  }
}
