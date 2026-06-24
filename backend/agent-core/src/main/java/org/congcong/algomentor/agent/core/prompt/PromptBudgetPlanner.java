package org.congcong.algomentor.agent.core.prompt;

import java.util.List;

public interface PromptBudgetPlanner {

  List<PromptBudgetDecision> plan(
      PromptAssemblyRequest request,
      PromptProfile profile,
      List<RenderedPromptSection> renderedSections
  );
}
