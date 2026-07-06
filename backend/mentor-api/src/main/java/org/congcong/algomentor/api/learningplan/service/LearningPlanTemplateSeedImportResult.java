package org.congcong.algomentor.api.learningplan.service;

public record LearningPlanTemplateSeedImportResult(
    int templateCount,
    int problemRefCount,
    int matchedProblemCount,
    int missingProblemCount
) {
}
