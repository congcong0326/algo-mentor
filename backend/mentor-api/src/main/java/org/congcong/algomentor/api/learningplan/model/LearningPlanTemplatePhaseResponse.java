package org.congcong.algomentor.api.learningplan.model;

import java.util.List;

public record LearningPlanTemplatePhaseResponse(
    int phaseIndex,
    String title,
    int durationWeeks,
    String focus,
    int plannedProblemCount
) {
}
