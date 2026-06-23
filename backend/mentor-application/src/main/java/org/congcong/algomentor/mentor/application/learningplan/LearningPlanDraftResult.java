package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public record LearningPlanDraftResult(
    long draftId,
    LearningPlanDraftStatus status,
    String assistantMessage,
    List<String> missingFields,
    LearningPlanDraftPlan draftPlan
) {

  public LearningPlanDraftResult {
    missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
  }

  public static LearningPlanDraftResult fromDraft(LearningPlanDraft draft) {
    return new LearningPlanDraftResult(
        draft.id(),
        draft.status(),
        draft.assistantMessage(),
        draft.missingFields(),
        draft.draftPlan());
  }
}
