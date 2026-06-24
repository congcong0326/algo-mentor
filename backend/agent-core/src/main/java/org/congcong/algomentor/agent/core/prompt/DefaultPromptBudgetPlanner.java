package org.congcong.algomentor.agent.core.prompt;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultPromptBudgetPlanner implements PromptBudgetPlanner {

  @Override
  public List<PromptBudgetDecision> plan(
      PromptAssemblyRequest request,
      PromptProfile profile,
      List<RenderedPromptSection> renderedSections
  ) {
    int budget = request.tokenBudget() > 0 ? request.tokenBudget() : profile.tokenBudget();
    int[] usedTokens = {0};
    Map<String, PromptBudgetDecision> decisions = new HashMap<>();

    renderedSections.stream()
        .sorted(Comparator
            .comparingInt((RenderedPromptSection rendered) -> rendered.section().priority())
            .thenComparing(rendered -> rendered.section().id()))
        .forEach(rendered -> decisions.put(
            rendered.section().id(),
            decide(rendered, budget, usedTokens)));

    return renderedSections.stream()
        .map(rendered -> decisions.get(rendered.section().id()))
        .toList();
  }

  private PromptBudgetDecision decide(RenderedPromptSection rendered, int budget, int[] usedTokens) {
    PromptSection section = rendered.section();
    if (usedTokens[0] + rendered.tokenEstimate() <= budget) {
      usedTokens[0] += rendered.tokenEstimate();
      return PromptBudgetDecision.keep(section.id());
    }

    int remaining = Math.max(0, budget - usedTokens[0]);
    PromptBudgetDecision decision = overBudgetDecision(section, remaining);
    if (decision.included()) {
      usedTokens[0] += Math.min(rendered.tokenEstimate(), decision.tokenLimit());
    }
    return decision;
  }

  private PromptBudgetDecision overBudgetDecision(PromptSection section, int remainingTokens) {
    return switch (section.budgetPolicy()) {
      case KEEP, FAIL_IF_OVER_BUDGET -> failOrDrop(section, "required section exceeds prompt token budget");
      case DROP_IF_NEEDED -> failOrDrop(section, "section dropped because prompt token budget is exhausted");
      case TRUNCATE_IF_NEEDED -> partialOrFail(section, remainingTokens, PromptBudgetAction.TRUNCATE);
      case EXTRACT_IF_NEEDED -> partialOrFail(section, remainingTokens, PromptBudgetAction.EXTRACT);
      case SUMMARIZE_IF_NEEDED -> partialOrFail(section, remainingTokens, PromptBudgetAction.SUMMARIZE);
    };
  }

  private PromptBudgetDecision failOrDrop(PromptSection section, String reason) {
    if (section.required()) {
      return PromptBudgetDecision.failRequired(section.id(), reason);
    }
    return PromptBudgetDecision.drop(section.id(), reason);
  }

  private PromptBudgetDecision partialOrFail(
      PromptSection section,
      int remainingTokens,
      PromptBudgetAction action
  ) {
    if (remainingTokens < 1) {
      return failOrDrop(section, "section has no remaining prompt token budget");
    }
    String reason = "section reduced to fit prompt token budget";
    return switch (action) {
      case TRUNCATE -> PromptBudgetDecision.truncate(section.id(), remainingTokens, reason);
      case EXTRACT -> PromptBudgetDecision.extract(section.id(), remainingTokens, reason);
      case SUMMARIZE -> PromptBudgetDecision.summarize(section.id(), remainingTokens, reason);
      default -> throw new IllegalArgumentException("Unsupported partial budget action: " + action);
    };
  }
}
