package org.congcong.algomentor.api.practice.mapper.model;

import java.math.BigDecimal;
import java.time.Instant;

/** 历史提交 Tool 详情查询的受限正式 Review 行，唯一额外字段是归一化代码。 */
public record PracticeSubmissionHistoryDetailRow(
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
    String reviewHistorySummary,
    String normalizedCode
) {
}
