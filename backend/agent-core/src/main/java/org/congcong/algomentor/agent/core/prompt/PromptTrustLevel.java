package org.congcong.algomentor.agent.core.prompt;

public enum PromptTrustLevel {
  SYSTEM_STATIC,
  SERVER_VALIDATED,
  MODEL_GENERATED,
  TOOL_OUTPUT,
  USER_INPUT
}
