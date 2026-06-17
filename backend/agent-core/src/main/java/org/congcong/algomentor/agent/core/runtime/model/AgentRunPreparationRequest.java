package org.congcong.algomentor.agent.core.runtime.model;

import java.util.Map;

public record AgentRunPreparationRequest(
    Long taskId,
    Long userId,
    String userMessage,
    String idempotencyKey,
    String systemPrompt,
    Map<String, Object> metadata
) {

  public AgentRunPreparationRequest {
    if (userMessage == null || userMessage.isBlank()) {
      throw new IllegalArgumentException("Agent run user message must not be blank");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Agent run idempotency key must not be blank");
    }
    if (taskId != null && taskId < 1) {
      throw new IllegalArgumentException("Agent task id must be positive");
    }
    if (userId != null && userId < 1) {
      throw new IllegalArgumentException("Agent user id must be positive");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
