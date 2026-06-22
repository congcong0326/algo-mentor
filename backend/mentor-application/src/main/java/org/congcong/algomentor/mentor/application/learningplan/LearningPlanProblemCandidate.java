package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public record LearningPlanProblemCandidate(
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    String difficulty,
    List<String> tags
) {

  public LearningPlanProblemCandidate {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
