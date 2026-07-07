package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public record LearningPlanTrainingPackage(
    int weekIndex,
    int newProblemCount,
    String reviewTask,
    int estimatedMinutes,
    List<String> priorityProblemSlugs
) {

  public LearningPlanTrainingPackage {
    priorityProblemSlugs = priorityProblemSlugs == null ? List.of() : List.copyOf(priorityProblemSlugs);
  }
}
