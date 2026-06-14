package org.congcong.algomentor.llm.core.model;

/**
 * 用于特定提供商 LLM 模型标识符的值对象。
 */
public record LlmModelId(String value) {

  public LlmModelId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("LLM model id must not be blank");
    }
    value = value.trim();
  }

  public static LlmModelId of(String value) {
    return new LlmModelId(value);
  }
}
