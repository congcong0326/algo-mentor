package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;

public record LearningPlanDetailProblemResponse(
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    String difficulty,
    List<String> tags,
    String reason,
    int sortOrder,
    PracticeProgressStatus progressStatus
) {

  public LearningPlanDetailProblemResponse {
    tags = tags == null ? List.of() : List.copyOf(tags);
    progressStatus = progressStatus == null ? PracticeProgressStatus.NOT_STARTED : progressStatus;
  }
}
