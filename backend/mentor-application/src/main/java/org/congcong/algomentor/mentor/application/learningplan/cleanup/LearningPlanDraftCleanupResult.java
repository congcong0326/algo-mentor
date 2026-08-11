package org.congcong.algomentor.mentor.application.learningplan.cleanup;

import java.time.Duration;

/** 单轮学习计划草案清理结果。 */
public record LearningPlanDraftCleanupResult(
    int deletedDrafts,
    int deletedDailyUsageRows,
    Duration duration
) {
}
