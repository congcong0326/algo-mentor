package org.congcong.algomentor.mentor.application.learningplan.policy;

import java.time.Instant;
import java.time.LocalDate;

/** 单次新建草案使用的配额日期、上限和保留期快照。 */
public record LearningPlanDraftCreationAdmission(
    LocalDate quotaDate,
    int dailyLimit,
    Instant expiresAt
) {
}
