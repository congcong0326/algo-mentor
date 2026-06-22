package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public record LearningPlanProblemDraft(
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    String difficulty,
    List<String> tags,
    String reason,
    int sortOrder
) {

  public LearningPlanProblemDraft {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }

  static LearningPlanProblemDraft fromCandidate(LearningPlanProblemCandidate candidate, int sortOrder, String reason) {
    return new LearningPlanProblemDraft(
        candidate.slug(),
        candidate.frontendId(),
        candidate.title(),
        candidate.titleCn(),
        candidate.difficulty(),
        candidate.tags(),
        reason,
        sortOrder);
  }
}
