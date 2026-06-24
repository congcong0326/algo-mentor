package org.congcong.algomentor.agent.core.prompt;

import java.util.List;

public enum PromptSlot {
  STATIC_INSTRUCTION,
  SCENARIO_POLICY,
  RUNTIME_CONTEXT,
  MEMORY_SUMMARY,
  HISTORY,
  TOOL_RESULT,
  CURRENT_USER_MESSAGE;

  public static List<PromptSlot> canonicalOrder() {
    return List.of(
        STATIC_INSTRUCTION,
        SCENARIO_POLICY,
        RUNTIME_CONTEXT,
        MEMORY_SUMMARY,
        HISTORY,
        TOOL_RESULT,
        CURRENT_USER_MESSAGE);
  }
}
