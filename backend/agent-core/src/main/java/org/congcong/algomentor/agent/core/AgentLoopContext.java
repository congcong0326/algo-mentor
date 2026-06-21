package org.congcong.algomentor.agent.core;

import java.util.Map;

public record AgentLoopContext(
    String runId,
    AgentRequest request,
    int maxSteps,
    Map<String, Object> metadata,
    AgentCancellationToken cancellationToken
) {

  public AgentLoopContext(String runId, AgentRequest request, int maxSteps, Map<String, Object> metadata) {
    this(runId, request, maxSteps, metadata, new AgentCancellationToken());
  }

  public AgentLoopContext {
    if (runId == null || runId.isBlank()) {
      throw new IllegalArgumentException("Agent loop run id must not be blank");
    }
    if (request == null) {
      throw new IllegalArgumentException("Agent loop request must not be null");
    }
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Agent loop max steps must be positive");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    cancellationToken = cancellationToken == null ? new AgentCancellationToken() : cancellationToken;
  }

  public boolean cancelled() {
    return cancellationToken.isCancelled();
  }
}
