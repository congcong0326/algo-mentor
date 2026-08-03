package org.congcong.algomentor.mentor.application.practice;

import java.math.BigDecimal;
import java.time.Instant;

/** 复习中心使用的轻量代码 Review 反向索引记录。 */
public record PracticeCodeReviewIndexEntry(
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
