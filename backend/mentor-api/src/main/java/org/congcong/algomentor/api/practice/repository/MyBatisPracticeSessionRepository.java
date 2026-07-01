package org.congcong.algomentor.api.practice.repository;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.api.practice.mapper.PracticeSessionMapper;
import org.congcong.algomentor.api.practice.mapper.model.PracticeProgressRow;
import org.congcong.algomentor.api.practice.mapper.model.PracticeSessionRow;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionStatus;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisPracticeSessionRepository implements PracticeSessionRepository {

  private final PracticeSessionMapper mapper;

  public MyBatisPracticeSessionRepository(PracticeSessionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
    return toProgress(mapper.upsertProgress(userId, planId, phaseIndex, problemSlug));
  }

  @Override
  @Transactional
  public PracticeSession upsertAndLockSession(
      long userId,
      long planId,
      int phaseIndex,
      String problemSlug,
      String locale
  ) {
    return toSession(mapper.upsertSession(userId, planId, phaseIndex, problemSlug, locale));
  }

  @Override
  public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
    return Optional.ofNullable(mapper.findSessionByIdForUser(sessionId, userId)).map(this::toSession);
  }

  @Override
  @Transactional
  public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
    return toSession(mapper.attachAgentTask(sessionId, agentTaskId));
  }

  @Override
  @Transactional
  public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
    return toSession(mapper.attachProblemStatementMessage(sessionId, messageId));
  }

  @Override
  @Transactional
  public PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status) {
    return toProgress(mapper.updateProgressStatus(sessionId, userId, status.name()));
  }

  @Override
  public List<PracticeProgress> findProgressByPlan(long userId, long planId) {
    return mapper.findProgressByPlan(userId, planId).stream().map(this::toProgress).toList();
  }

  @Override
  @Transactional
  public void touchLastMessageAt(long sessionId) {
    mapper.touchLastMessageAt(sessionId);
  }

  private PracticeProgress toProgress(PracticeProgressRow row) {
    return new PracticeProgress(
        row.id(),
        row.userId(),
        row.planId(),
        row.phaseIndex(),
        row.problemSlug(),
        PracticeProgressStatus.valueOf(row.status()),
        row.createdAt(),
        row.updatedAt());
  }

  private PracticeSession toSession(PracticeSessionRow row) {
    return new PracticeSession(
        row.id(),
        row.userId(),
        row.planId(),
        row.phaseIndex(),
        row.problemSlug(),
        PracticeSessionStatus.valueOf(row.status()),
        row.agentTaskId(),
        row.problemStatementMessageId(),
        PracticeProgressStatus.valueOf(row.progressStatus()),
        row.lastMessageAt(),
        row.createdAt(),
        row.updatedAt(),
        row.locale());
  }
}
