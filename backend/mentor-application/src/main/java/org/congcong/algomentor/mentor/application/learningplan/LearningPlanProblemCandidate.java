package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public record LearningPlanProblemCandidate(
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    String difficulty,
    List<String> tags,
    String recommendationReason
) {

  public LearningPlanProblemCandidate(
      String slug,
      Integer frontendId,
      String title,
      String titleCn,
      String difficulty,
      List<String> tags
  ) {
    this(slug, frontendId, title, titleCn, difficulty, tags, null);
  }

  public LearningPlanProblemCandidate {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
