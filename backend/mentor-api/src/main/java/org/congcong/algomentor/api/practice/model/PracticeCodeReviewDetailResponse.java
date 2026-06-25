package org.congcong.algomentor.api.practice.model;

import java.time.Instant;
import java.util.List;

public record PracticeCodeReviewDetailResponse(
    long id,
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
    List<PracticeCodeReviewEvidenceResponse> evidence,
    String contextSummary,
    PracticeCodeReviewScoreResponse scores,
    boolean passed,
    List<String> deductionReasons,
    List<String> improvementSuggestions,
    String reviewMarkdown,
    Instant createdAt) {
}
