package org.congcong.algomentor.mentor.application.learningplan;

import java.time.Instant;
import java.util.Optional;

public interface LearningPlanActivationRepository {

  Optional<LearningPlanActivation> findSelectionByUserId(long userId);

  LearningPlanActivation upsert(long userId, long planId, Instant activatedAt);
}
