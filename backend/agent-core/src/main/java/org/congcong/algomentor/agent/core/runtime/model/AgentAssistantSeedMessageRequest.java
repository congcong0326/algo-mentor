package org.congcong.algomentor.agent.core.runtime.model;

import java.util.Map;

public record AgentAssistantSeedMessageRequest(
    long taskId,
    String content,
    Map<String, Object> metadata
) {

  public AgentAssistantSeedMessageRequest {
    if (taskId < 1) {
      throw new IllegalArgumentException("Agent seed message task id must be positive");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Agent seed message content must not be blank");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
