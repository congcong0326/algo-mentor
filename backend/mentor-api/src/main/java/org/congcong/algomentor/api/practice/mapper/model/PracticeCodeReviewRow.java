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
    String contentLocale,
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
    Instant createdAt,
    String reviewHistorySummary
) {

  public PracticeCodeReviewRow(
      long id, long userId, long planId, int phaseIndex, String problemSlug, long sessionId, int versionNo,
      Long userMessageId, Long assistantMessageId, Long agentRunDbId, String rawCode, String normalizedCode,
      String language, String contentLocale, JsonNode detectionEvidenceJson, String contextSummary,
      BigDecimal totalScore, BigDecimal correctnessScore, BigDecimal complexityScore, BigDecimal edgeCaseScore,
      BigDecimal codeQualityScore, BigDecimal problemFitScore, boolean passed, JsonNode deductionReasonsJson,
      JsonNode improvementSuggestionsJson, String reviewMarkdown, Instant createdAt) {
    this(id, userId, planId, phaseIndex, problemSlug, sessionId, versionNo, userMessageId, assistantMessageId,
        agentRunDbId, rawCode, normalizedCode, language, contentLocale, detectionEvidenceJson, contextSummary,
        totalScore, correctnessScore, complexityScore, edgeCaseScore, codeQualityScore, problemFitScore, passed,
        deductionReasonsJson, improvementSuggestionsJson, reviewMarkdown, createdAt, null);
  }
}
