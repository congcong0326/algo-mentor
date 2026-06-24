package org.congcong.algomentor.agent.core.prompt;

public record PromptBudgetDecision(
    String sectionId,
    PromptBudgetAction action,
    int tokenLimit,
    String reason
) {

  public static PromptBudgetDecision keep(String sectionId) {
    return new PromptBudgetDecision(sectionId, PromptBudgetAction.KEEP, 0, "within budget");
  }

  public static PromptBudgetDecision drop(String sectionId, String reason) {
    return new PromptBudgetDecision(sectionId, PromptBudgetAction.DROP, 0, reason);
  }

  public static PromptBudgetDecision truncate(String sectionId, int tokenLimit, String reason) {
    return new PromptBudgetDecision(sectionId, PromptBudgetAction.TRUNCATE, tokenLimit, reason);
  }

  public static PromptBudgetDecision extract(String sectionId, int tokenLimit, String reason) {
    return new PromptBudgetDecision(sectionId, PromptBudgetAction.EXTRACT, tokenLimit, reason);
  }

  public static PromptBudgetDecision summarize(String sectionId, int tokenLimit, String reason) {
    return new PromptBudgetDecision(sectionId, PromptBudgetAction.SUMMARIZE, tokenLimit, reason);
  }

  public static PromptBudgetDecision failRequired(String sectionId, String reason) {
    return new PromptBudgetDecision(sectionId, PromptBudgetAction.FAIL_REQUIRED, 0, reason);
  }

  public PromptBudgetDecision {
    if (sectionId == null || sectionId.isBlank()) {
      throw new IllegalArgumentException("Prompt budget decision section id must not be blank");
    }
    if (action == null) {
      throw new IllegalArgumentException("Prompt budget decision action must not be null");
    }
    if (tokenLimit < 0) {
      throw new IllegalArgumentException("Prompt budget decision token limit must not be negative");
    }
    reason = reason == null ? "" : reason;
  }

  public boolean included() {
    return action != PromptBudgetAction.DROP && action != PromptBudgetAction.FAIL_REQUIRED;
  }

  public boolean truncated() {
    return action == PromptBudgetAction.TRUNCATE
        || action == PromptBudgetAction.EXTRACT
        || action == PromptBudgetAction.SUMMARIZE;
  }
}
