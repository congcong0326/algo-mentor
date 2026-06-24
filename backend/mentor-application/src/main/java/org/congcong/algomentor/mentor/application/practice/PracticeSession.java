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
    Instant updatedAt
) {

  public PracticeSession withAgentTaskId(long nextAgentTaskId) {
    return new PracticeSession(id, userId, planId, phaseIndex, problemSlug, status, nextAgentTaskId,
        problemStatementMessageId, progressStatus, lastMessageAt, createdAt, updatedAt);
  }

  public PracticeSession withProblemStatementMessageId(long nextProblemStatementMessageId) {
    return new PracticeSession(id, userId, planId, phaseIndex, problemSlug, status, agentTaskId,
        nextProblemStatementMessageId, progressStatus, lastMessageAt, createdAt, updatedAt);
  }
}
