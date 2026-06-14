package org.congcong.algomentor.llm.core.response;

/**
 * Token accounting returned by an LLM provider for a request or stream.
 */
public record LlmUsage(
    int inputTokens,
    int outputTokens,
    int cachedTokens,
    int reasoningTokens,
    Integer totalTokens
) {

  public LlmUsage {
    if (inputTokens < 0 || outputTokens < 0 || cachedTokens < 0 || reasoningTokens < 0) {
      throw new IllegalArgumentException("LLM token usage values must not be negative");
    }
    if (totalTokens == null) {
      totalTokens = inputTokens + outputTokens;
    }
    if (totalTokens < 0) {
      throw new IllegalArgumentException("LLM total token usage must not be negative");
    }
  }

  public static LlmUsage empty() {
    return new LlmUsage(0, 0, 0, 0, 0);
  }
}
