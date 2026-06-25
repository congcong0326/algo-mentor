package org.congcong.algomentor.api.practice.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;

public record PracticeCodeReviewInsertRow(
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    long sessionId,
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
    String reviewMarkdown
) {
}
