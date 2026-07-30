package org.congcong.algomentor.mentor.application.conversation;

/** 普通导师会话 Definition 的受信业务输入。 */
public record MentorConversationAgentInput(
    Long taskId,
    long userId,
    String userMessage,
    String idempotencyKey,
    int requestSize
) {

  public MentorConversationAgentInput {
    if (taskId != null && taskId < 1) {
      throw new IllegalArgumentException("Mentor conversation task id must be positive");
    }
    if (userId < 1) {
      throw new IllegalArgumentException("Mentor conversation user id must be positive");
    }
    if (userMessage == null || userMessage.isBlank()) {
      throw new IllegalArgumentException("Mentor conversation user message must not be blank");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Mentor conversation idempotency key must not be blank");
    }
    if (requestSize < 0) {
      throw new IllegalArgumentException("Mentor conversation request size must not be negative");
    }
    userMessage = userMessage.trim();
    idempotencyKey = idempotencyKey.trim();
  }
}
