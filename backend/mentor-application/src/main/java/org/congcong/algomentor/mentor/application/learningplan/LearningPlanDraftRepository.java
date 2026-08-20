package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LearningPlanDraftRepository {

  LearningPlanDraft save(LearningPlanDraft draft);

  /** 在同一事务内消耗每日额度并插入草案；额度不足时返回空。 */
  default Optional<LearningPlanDraft> createWithinDailyLimit(
      LearningPlanDraft draft,
      LocalDate quotaDate,
      int dailyLimit,
      Instant consumedAt
  ) {
    if (dailyLimit < 1) {
      return Optional.empty();
    }
    return Optional.of(save(draft));
  }

  Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId);

  /** 按首次 AI 创建幂等键读取草案；幂等键仅在首次创建链路写入。 */
  default Optional<LearningPlanDraft> findDraftByGenerationRequestKey(long userId, String requestKey) {
    return Optional.empty();
  }

  /** 为同用户同首次创建幂等键建立事务级互斥；非 PostgreSQL 测试实现可保持无操作。 */
  default void lockGenerationRequest(long userId, String requestKey) {
  }

  /** 仅允许 GENERATING 草案进入成功终态，防止迟到 Agent 事件覆盖已提交状态。 */
  default Optional<LearningPlanDraft> completeGeneration(
      LearningPlanDraft draft,
      LearningPlanDraftPlan plan,
      Instant completedAt
  ) {
    if (draft.status() != LearningPlanDraftStatus.GENERATING) {
      return Optional.empty();
    }
    return Optional.of(save(draft.withGenerationSucceeded(plan, completedAt)));
  }

  /** 仅允许 GENERATING 草案进入失败终态，错误内容必须已经过业务层脱敏。 */
  default Optional<LearningPlanDraft> failGeneration(
      LearningPlanDraft draft,
      String code,
      String message,
      Instant completedAt
  ) {
    if (draft.status() != LearningPlanDraftStatus.GENERATING) {
      return Optional.empty();
    }
    return Optional.of(save(draft.withGenerationFailed(code, message, completedAt)));
  }

  /**
   * 应用重启时收敛超过阈值仍在运行的草案。实现不得续跑 Agent，并只返回实际完成条件更新的草案 ID。
   */
  default List<Long> failInterruptedGenerations(
      Instant startedBefore,
      String code,
      String message,
      Instant completedAt
  ) {
    return List.of();
  }

  default Optional<LearningPlanDraft> findDraftByIdForUserForUpdate(long draftId, long userId) {
    return findDraftByIdForUser(draftId, userId);
  }

  default int deleteExpiredDrafts(Instant expiredBefore, int limit) {
    throw new LearningPlanRepositoryUnavailableException();
  }

  default int deleteDailyDraftUsageBefore(LocalDate quotaDate, int limit) {
    throw new LearningPlanRepositoryUnavailableException();
  }
}
