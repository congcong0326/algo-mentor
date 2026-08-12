package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftSource;

public record LearningPlanDraftResponse(
    long draftId,
    LearningPlanDraftSource source,
    LearningPlanDraftStatus status,
    String assistantMessage,
    List<String> missingFields,
    LearningPlanDraftPlanResponse draftPlan
) {
}
