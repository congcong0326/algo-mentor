package org.congcong.algomentor.agent.core;

import java.util.Map;

public record AgentExecutionContext(
    String runId,
    int stepIndex,
    Map<String, Object> requestMetadata,
    boolean cancelled
) {

  public AgentExecutionContext {
    if (runId == null || runId.isBlank()) {
      throw new IllegalArgumentException("Agent execution run id must not be blank");
    }
    if (stepIndex < 1) {
      throw new IllegalArgumentException("Agent execution step index must be positive");
    }
    requestMetadata = requestMetadata == null ? Map.of() : Map.copyOf(requestMetadata);
  }
}
