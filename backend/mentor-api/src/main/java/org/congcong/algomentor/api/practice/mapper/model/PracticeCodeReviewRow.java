package org.congcong.algomentor.api.practice.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;

public record PracticeCodeReviewRow(
    long id,
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    long sessionId,
    int versionNo,
    Long userMessageId,
    Long assistantMessageId,
    Long agentRunDbId,
    String rawCode,
    String normalizedCode,
    String language,
    JsonNode detectionEvidenceJson,
    String contextSummary,
    BigDecimal totalScore,
    BigDecimal correctnessScore,
    BigDecimal complexityScore,
    BigDecimal edgeCaseScore,
    BigDecimal codeQualityScore,
    BigDecimal problemFitScore,
    boolean passed,
    JsonNode deductionReasonsJson,
    JsonNode improvementSuggestionsJson,
    String reviewMarkdown,
    Instant createdAt
) {
}
