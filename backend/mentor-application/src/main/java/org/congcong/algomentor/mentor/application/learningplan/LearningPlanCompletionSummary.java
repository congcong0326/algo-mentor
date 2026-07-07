package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public record LearningPlanCompletionSummary(
    double completionRate,
    long totalDurationDays,
    int completedProblemCount,
    int skippedProblemCount,
    int openProblemCount,
    List<String> strongTags,
    List<String> weakTags,
    List<String> unresolvedProblemSlugs
) {

  public LearningPlanCompletionSummary {
    strongTags = strongTags == null ? List.of() : List.copyOf(strongTags);
    weakTags = weakTags == null ? List.of() : List.copyOf(weakTags);
    unresolvedProblemSlugs = unresolvedProblemSlugs == null ? List.of() : List.copyOf(unresolvedProblemSlugs);
  }
}
