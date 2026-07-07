package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public record LearningPlanWeeklyBucket(
    int weekIndex,
    String title,
    int plannedProblemCount,
    double plannedLoadPoints,
    List<String> problemSlugs,
    String reviewAdvice
) {

  public LearningPlanWeeklyBucket {
    problemSlugs = problemSlugs == null ? List.of() : List.copyOf(problemSlugs);
  }
}
