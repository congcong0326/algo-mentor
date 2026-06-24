package org.congcong.algomentor.agent.core.prompt;

public enum PromptBudgetPolicy {
  KEEP,
  DROP_IF_NEEDED,
  TRUNCATE_IF_NEEDED,
  EXTRACT_IF_NEEDED,
  SUMMARIZE_IF_NEEDED,
  FAIL_IF_OVER_BUDGET
}
