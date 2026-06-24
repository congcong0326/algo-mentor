package org.congcong.algomentor.mentor.application.practice;

import java.time.Instant;

public record PracticeSession(
    long id,
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    PracticeSessionStatus status,
    Long agentTaskId,
    Long problemStatementMessageId,
    PracticeProgressStatus progressStatus,
    Instant lastMessageAt,
    Instant createdAt,
    Instant updatedAt,
    String locale
) {

  public PracticeSession {
    if (id < 1) {
      throw new IllegalArgumentException("Practice session id must be positive");
    }
    if (userId < 1) {
      throw new IllegalArgumentException("Practice session user id must be positive");
    }
    if (planId < 1) {
      throw new IllegalArgumentException("Practice session plan id must be positive");
    }
    if (phaseIndex < 1) {
      throw new IllegalArgumentException("Practice session phase index must be positive");
    }
    if (problemSlug == null || problemSlug.isBlank()) {
      throw new IllegalArgumentException("Practice session problem slug must not be blank");
    }
    if (status == null) {
      throw new IllegalArgumentException("Practice session status must not be null");
    }
    if (progressStatus == null) {
      throw new IllegalArgumentException("Practice session progress status must not be null");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("Practice session created time must not be null");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("Practice session updated time must not be null");
    }
    problemSlug = problemSlug.trim();
    locale = locale == null || locale.isBlank() ? "zh-CN" : locale.trim();
  }

  public PracticeSession withAgentTaskId(long nextAgentTaskId) {
    return new PracticeSession(id, userId, planId, phaseIndex, problemSlug, status, nextAgentTaskId,
        problemStatementMessageId, progressStatus, lastMessageAt, createdAt, updatedAt, locale);
  }

  public PracticeSession withProblemStatementMessageId(long nextProblemStatementMessageId) {
    return new PracticeSession(id, userId, planId, phaseIndex, problemSlug, status, agentTaskId,
        nextProblemStatementMessageId, progressStatus, lastMessageAt, createdAt, updatedAt, locale);
  }
}
