package org.congcong.algomentor.api.practice.mapper.model;

import java.math.BigDecimal;
import java.time.Instant;

public record CodeReviewEvidenceDetailRow(
    long reviewId,
    String problemSlug,
    int versionNo,
    BigDecimal totalScore,
    BigDecimal correctnessScore,
    BigDecimal complexityScore,
    BigDecimal edgeCaseScore,
    BigDecimal codeQualityScore,
    BigDecimal problemFitScore,
    boolean passed,
    String deductionReasonsJson,
    String improvementSuggestionsJson,
    String affectedTagIdsJson,
    Instant createdAt,
    String detectionEvidenceJson,
    String contextSummary
) {
}
