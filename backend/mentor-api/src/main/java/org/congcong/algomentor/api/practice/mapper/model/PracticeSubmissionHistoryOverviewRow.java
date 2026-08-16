package org.congcong.algomentor.api.practice.mapper.model;

import java.math.BigDecimal;
import java.time.Instant;

/** 历史提交 Tool 总览查询的跨计划聚合行。 */
public record PracticeSubmissionHistoryOverviewRow(
    long formalSubmissionCount,
    long passedSubmissionCount,
    Instant firstSubmittedAt,
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
