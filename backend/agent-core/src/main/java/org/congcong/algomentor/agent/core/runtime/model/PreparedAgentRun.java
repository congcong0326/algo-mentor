package org.congcong.algomentor.agent.core.runtime.model;

import java.util.Map;

public record PreparedAgentRun(
    long taskId,
    long turnId,
    long runId,
    String runUuid,
    String requestId,
    String systemPrompt,
    String activeSummary,
    Map<String, Object> metadata
) {

  public PreparedAgentRun {
    if (taskId < 1 || turnId < 1 || runId < 1) {
      throw new IllegalArgumentException("Conversation draft ids must be positive");
    }
    if (runUuid == null || runUuid.isBlank()) {
      throw new IllegalArgumentException("Conversation draft run uuid must not be blank");
    }
    if (requestId != null && requestId.isBlank()) {
      throw new IllegalArgumentException("Conversation draft request id must not be blank");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
