package org.congcong.algomentor.api.practice.mapper.model;

import java.math.BigDecimal;
import java.time.Instant;

/** 复习中心代码 Review 时间线的 MyBatis 窄行模型。 */
public record PracticeCodeReviewIndexRow(
    long reviewId,
    long planId,
    int phaseIndex,
    String problemSlug,
    long practiceSessionId,
    int versionNo,
    String language,
    String contentLocale,
    BigDecimal totalScore,
    boolean passed,
    String primaryFeedback,
    Instant createdAt
) {
}
