package org.congcong.algomentor.llm.core;

public enum LlmFinishReason {
  STOP,
  LENGTH,
  TOOL_CALLS,
  CONTENT_FILTER,
  ERROR,
  UNKNOWN
}
