package org.congcong.algomentor.api.practice.mapper.model;

import java.math.BigDecimal;
import java.time.Instant;

/** 历史提交 Tool 列表查询的单条正式 Review 窄行。 */
public record PracticeSubmissionHistorySubmissionRow(
    long reviewId,
    Instant submittedAt,
    String language,
    BigDecimal totalScore,
    boolean passed,
    BigDecimal correctnessScore,
    BigDecimal complexityScore,
    BigDecimal edgeCaseScore,
    BigDecimal codeQualityScore,
    BigDecimal problemFitScore,
    String deductionReasonsJson,
    String improvementSuggestionsJson,
    String affectedTagIdsJson,
    String reviewHistorySummary
) {
}
