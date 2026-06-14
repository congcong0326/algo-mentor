package org.congcong.algomentor.llm.core;

/**
 * Compatibility API retained during migration to the provider/gateway completion contract.
 */
@Deprecated(forRemoval = false)
public record LlmResponse(String content) {

  public LlmResponse {
    if (content == null) {
      throw new IllegalArgumentException("LLM response content must not be null");
    }
  }
}
