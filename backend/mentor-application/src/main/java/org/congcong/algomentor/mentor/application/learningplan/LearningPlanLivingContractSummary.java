package org.congcong.algomentor.mentor.application.learningplan;

import java.time.LocalDate;

public record LearningPlanLivingContractSummary(
    int totalProblemCount,
    int completedProblemCount,
    int skippedProblemCount,
    int openProblemCount,
    double progressPercent,
    LocalDate estimatedCompletionDate,
    LearningPlanEstimationSource estimationSource,
    LearningPlanVisibleStatus visibleStatus,
    LearningPlanTrainingPackage nextTrainingPackage,
    String notice,
    LearningPlanCompletionSummary completionSummary
) {
}
