package org.congcong.algomentor.llm.core;

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
