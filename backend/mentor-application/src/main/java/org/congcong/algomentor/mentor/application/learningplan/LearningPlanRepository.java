package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;
import java.util.Optional;

public interface LearningPlanRepository {

  LearningPlan save(LearningPlan plan);

  List<LearningPlan> findByUserId(long userId);

  Optional<LearningPlan> findPlanByIdForUser(long planId, long userId);
}
