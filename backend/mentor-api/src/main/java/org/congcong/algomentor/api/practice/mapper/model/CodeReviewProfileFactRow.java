package org.congcong.algomentor.api.practice.mapper.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Profile consumer 专用轻量 Review SQL 投影，禁止加入代码或完整 Markdown 字段。 */
public record CodeReviewProfileFactRow(
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
    Instant createdAt
) {
}
