package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;
import java.time.LocalDate;
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
