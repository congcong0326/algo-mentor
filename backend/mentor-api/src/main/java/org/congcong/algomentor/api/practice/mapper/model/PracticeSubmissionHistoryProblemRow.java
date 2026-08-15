package org.congcong.algomentor.api.practice.mapper.model;

import java.time.Instant;

/** Practice Chat 历史提交索引的每题最新 Review 窄行。 */
public record PracticeSubmissionHistoryProblemRow(
    long reviewId,
    String problemSlug,
    String reviewHistorySummary,
    Instant createdAt
) {
}
