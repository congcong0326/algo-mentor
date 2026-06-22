package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;
import java.util.Optional;

public interface LearningPlanProblemCatalog {

  List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search);

  Optional<LearningPlanProblemCandidate> findBySlug(String slug);
}
