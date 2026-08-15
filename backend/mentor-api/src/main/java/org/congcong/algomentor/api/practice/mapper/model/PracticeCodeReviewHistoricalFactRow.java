package org.congcong.algomentor.api.practice.mapper.model;

import java.time.Instant;

/** 生成当前 Review 历程摘要的最小历史读取行。 */
public record PracticeCodeReviewHistoricalFactRow(
    long reviewId,
    boolean passed,
    String primaryFinding,
    Instant createdAt
) {
}
