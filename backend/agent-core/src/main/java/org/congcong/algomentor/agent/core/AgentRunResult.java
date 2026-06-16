package org.congcong.algomentor.agent.core;

import java.util.Map;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;

public record AgentRunResult(
    int steps,
    LlmFinishReason finishReason,
    Map<String, Object> metadata
) {

  public AgentRunResult {
    if (steps < 1) {
      throw new IllegalArgumentException("Agent run steps must be positive");
    }
    finishReason = finishReason == null ? LlmFinishReason.UNKNOWN : finishReason;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
