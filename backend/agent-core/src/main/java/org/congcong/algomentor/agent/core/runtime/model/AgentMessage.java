package org.congcong.algomentor.agent.core.runtime.model;

import java.time.Instant;

public record AgentMessage(
    long id,
    long taskId,
    long sequenceNo,
    Role role,
    String content,
    Instant createdAt
) {

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
  }

  public enum Role {
    USER,
    ASSISTANT
  }
}
