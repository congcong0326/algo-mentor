package org.congcong.algomentor.api.learningplan.model;

import java.util.Map;

public record LearningPlanTemplateProblemRefResponse(
    int phaseIndex,
    int sortOrder,
    int sourceOrder,
    String problemSlug,
    String sourceTitle,
    String sourceDifficulty,
    String pattern,
    String sourceUrl,
    boolean matchedProblem,
    Map<String, Object> metadata
) {
}
