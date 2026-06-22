package org.congcong.algomentor.mentor.application.learningplan;

import java.util.Optional;

public interface LearningPlanDraftRepository {

  LearningPlanDraft save(LearningPlanDraft draft);

  Optional<LearningPlanDraft> findDraftByIdForUser(long draftId, long userId);
}
