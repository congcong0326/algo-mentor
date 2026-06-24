package org.congcong.algomentor.agent.core.prompt;

public interface PromptRenderer {

  RenderedPromptSection render(PromptSection section, PromptBudgetDecision budgetDecision);

  default RenderedPromptSection render(PromptSection section) {
    return render(section, PromptBudgetDecision.keep(section.id()));
  }
}
