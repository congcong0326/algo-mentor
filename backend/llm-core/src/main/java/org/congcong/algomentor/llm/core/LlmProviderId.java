package org.congcong.algomentor.llm.core;

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
