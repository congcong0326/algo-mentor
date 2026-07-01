package org.congcong.algomentor.mentor.application.practice;

import java.util.List;
import java.util.Optional;

public interface PracticeSessionRepository {

  PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug);

  PracticeSession upsertAndLockSession(long userId, long planId, int phaseIndex, String problemSlug, String locale);

  Optional<PracticeSession> findSessionForUser(long sessionId, long userId);

  PracticeSession attachAgentTask(long sessionId, long agentTaskId);

  PracticeSession attachProblemStatementMessage(long sessionId, long messageId);

  PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status);

  default List<PracticeProgress> findProgressByPlan(long userId, long planId) {
    throw new UnsupportedOperationException("Practice progress lookup by plan is not available");
  }

  void touchLastMessageAt(long sessionId);
}
