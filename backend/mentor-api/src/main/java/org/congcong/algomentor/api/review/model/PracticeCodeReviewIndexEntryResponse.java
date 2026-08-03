package org.congcong.algomentor.api.review.model;

import java.math.BigDecimal;
import java.time.Instant;

/** 复习中心单个代码 Review 时间线点的 API 响应。 */
public record PracticeCodeReviewIndexEntryResponse(
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
