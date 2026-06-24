package org.congcong.algomentor.agent.core.prompt;

import java.util.Map;

public record PromptAssemblyRequest(
    String scenario,
    String profileHint,
    int tokenBudget,
    Map<String, Object> variables,
    Map<String, Object> metadata
) {

  public PromptAssemblyRequest {
    if (scenario == null || scenario.isBlank()) {
      throw new IllegalArgumentException("Prompt assembly scenario must not be blank");
    }
    if (profileHint != null && profileHint.isBlank()) {
      throw new IllegalArgumentException("Prompt assembly profile hint must not be blank");
    }
    if (tokenBudget < 0) {
      throw new IllegalArgumentException("Prompt assembly token budget must not be negative");
    }
    variables = variables == null ? Map.of() : Map.copyOf(variables);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
