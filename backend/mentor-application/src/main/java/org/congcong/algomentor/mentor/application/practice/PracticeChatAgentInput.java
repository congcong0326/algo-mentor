package org.congcong.algomentor.mentor.application.practice;

/** Practice Chat Definition 的受信业务输入，仅由已完成 session 前检的应用服务构造。 */
public record PracticeChatAgentInput(
    long userId,
    long practiceSessionId,
    long agentTaskId,
    long planId,
    int phaseIndex,
    String problemSlug,
    String userMessage,
    String idempotencyKey,
    String locale,
    PracticeCoachStyle coachStyle,
    PracticeResponseLanguage responseLanguage,
    int requestSize
) {

  public PracticeChatAgentInput {
    if (userId < 1 || practiceSessionId < 1 || agentTaskId < 1 || planId < 1) {
      throw new IllegalArgumentException("Practice chat identity values must be positive");
    }
    if (phaseIndex < 1) {
      throw new IllegalArgumentException("Practice chat phase index must be positive");
    }
    if (problemSlug == null || problemSlug.isBlank()) {
      throw new IllegalArgumentException("Practice chat problem slug must not be blank");
    }
    if (userMessage == null || userMessage.isBlank()) {
      throw new IllegalArgumentException("Practice chat user message must not be blank");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Practice chat idempotency key must not be blank");
    }
    if (locale == null || locale.isBlank()) {
      throw new IllegalArgumentException("Practice chat locale must not be blank");
    }
    if (coachStyle == null || responseLanguage == null) {
      throw new IllegalArgumentException("Practice chat presentation preferences must not be null");
    }
    if (requestSize < 0) {
      throw new IllegalArgumentException("Practice chat request size must not be negative");
    }
    problemSlug = problemSlug.trim();
    userMessage = userMessage.trim();
    idempotencyKey = idempotencyKey.trim();
    locale = locale.trim();
  }
}
