package org.congcong.algomentor.mentor.application.learningplan;

import java.util.Optional;

public interface LearningPlanDraftRepository {

  LearningPlanDraft save(LearningPlanDraft draft);

  Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId);

  default Optional<LearningPlanDraft> findDraftByIdForUserForUpdate(long draftId, long userId) {
    return findDraftByIdForUser(draftId, userId);
  }
}
