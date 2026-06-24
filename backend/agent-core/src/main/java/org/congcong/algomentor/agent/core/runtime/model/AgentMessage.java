package org.congcong.algomentor.agent.core.runtime.model;

import java.time.Instant;
import java.util.Map;

public record AgentMessage(
    long id,
    long taskId,
    long sequenceNo,
    Role role,
    String content,
    Instant createdAt,
    Map<String, Object> metadata
) {

  public AgentMessage(long id, long taskId, long sequenceNo, Role role, String content, Instant createdAt) {
    this(id, taskId, sequenceNo, role, content, createdAt, Map.of());
  }

  public AgentMessage {
    if (id < 1) {
      throw new IllegalArgumentException("Conversation message id must be positive");
    }
    if (taskId < 1) {
      throw new IllegalArgumentException("Conversation message task id must be positive");
    }
    if (sequenceNo < 1) {
      throw new IllegalArgumentException("Conversation message sequence must be positive");
    }
    if (role == null) {
      throw new IllegalArgumentException("Conversation message role must not be null");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("Conversation message content must not be blank");
    }
    createdAt = createdAt == null ? Instant.EPOCH : createdAt;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public enum Role {
    USER,
    ASSISTANT
  }
}
