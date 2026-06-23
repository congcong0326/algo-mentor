package org.congcong.algomentor.mentor.application.conversation;

import java.util.Map;

public record AgentConversationCommand(
    Long taskId,
    Long userId,
    String userMessage,
    String idempotencyKey,
    Map<String, Object> governanceMetadata
) {

  public AgentConversationCommand(Long taskId, Long userId, String userMessage, String idempotencyKey) {
    this(taskId, userId, userMessage, idempotencyKey, Map.of());
  }

  public AgentConversationCommand {
    if (userMessage == null || userMessage.isBlank()) {
      throw new IllegalArgumentException("Conversation user message must not be blank");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Conversation idempotency key must not be blank");
    }
    if (taskId != null && taskId < 1) {
      throw new IllegalArgumentException("Conversation task id must be positive");
    }
    if (userId == null || userId < 1) {
      throw new IllegalArgumentException("Conversation user id must be positive");
    }
    governanceMetadata = governanceMetadata == null ? Map.of() : Map.copyOf(governanceMetadata);
  }
}
