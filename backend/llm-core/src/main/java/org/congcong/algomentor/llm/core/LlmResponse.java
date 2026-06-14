package org.congcong.algomentor.llm.core;

public record LlmResponse(String content) {

  public LlmResponse {
    if (content == null) {
      throw new IllegalArgumentException("LLM response content must not be null");
    }
  }
}
