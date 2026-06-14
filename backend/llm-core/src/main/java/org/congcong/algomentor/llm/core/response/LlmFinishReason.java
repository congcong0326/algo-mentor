package org.congcong.algomentor.llm.core.response;

/**
 * Normalized reason why a model stopped producing output.
 */
public enum LlmFinishReason {
  STOP,
  LENGTH,
  TOOL_CALLS,
  CONTENT_FILTER,
  ERROR,
  UNKNOWN
}
