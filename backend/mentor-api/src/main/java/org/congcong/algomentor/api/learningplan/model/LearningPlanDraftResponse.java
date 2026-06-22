package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;

public record LearningPlanDraftResponse(
    long draftId,
    LearningPlanDraftStatus status,
    String assistantMessage,
    List<String> missingFields,
    LearningPlanDraftPlan draftPlan
) {
}
