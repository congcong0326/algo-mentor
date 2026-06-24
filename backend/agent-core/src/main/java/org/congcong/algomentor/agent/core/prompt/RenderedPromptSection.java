package org.congcong.algomentor.agent.core.prompt;

public record RenderedPromptSection(
    PromptSection section,
    String renderedText,
    int charCount,
    int tokenEstimate,
    PromptBudgetDecision budgetDecision
) {

  public RenderedPromptSection {
    if (section == null) {
      throw new IllegalArgumentException("Rendered prompt section source section must not be null");
    }
    renderedText = renderedText == null ? "" : renderedText;
    if (charCount < 0) {
      throw new IllegalArgumentException("Rendered prompt section char count must not be negative");
    }
    if (tokenEstimate < 0) {
      throw new IllegalArgumentException("Rendered prompt section token estimate must not be negative");
    }
    if (budgetDecision == null) {
      throw new IllegalArgumentException("Rendered prompt section budget decision must not be null");
    }
  }

  public boolean included() {
    return budgetDecision.included() && !renderedText.isBlank();
  }
}
