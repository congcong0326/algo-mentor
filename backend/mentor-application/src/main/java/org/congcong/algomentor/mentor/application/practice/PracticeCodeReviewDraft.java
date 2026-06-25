package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

public record PracticeCodeReviewDraft(
    long userId,
    long planId,
    int phaseIndex,
    String problemSlug,
    long sessionId,
    long userMessageId,
    Long assistantMessageId,
    Long agentRunDbId,
    String rawCode,
    String normalizedCode,
    String language,
    List<PracticeCodeReviewEvidence> evidence,
    String contextSummary,
    PracticeCodeReviewScore score,
    boolean passed,
    List<String> deductionReasons,
    List<String> improvementSuggestions,
    String reviewMarkdown
) {

  public PracticeCodeReviewDraft {
    requirePositive(userId, "user id");
    requirePositive(planId, "plan id");
    if (phaseIndex < 1) {
      throw new IllegalArgumentException("Practice code review phase index must be positive");
    }
    if (problemSlug == null || problemSlug.isBlank()) {
      throw new IllegalArgumentException("Practice code review problem slug must not be blank");
    }
    requirePositive(sessionId, "session id");
    requirePositive(userMessageId, "user message id");
    requirePositiveIfPresent(assistantMessageId, "assistant message id");
    requirePositiveIfPresent(agentRunDbId, "agent run database id");
    if (rawCode == null || rawCode.isBlank()) {
      throw new IllegalArgumentException("Practice code review raw code must not be blank");
    }
    if (language == null || language.isBlank()) {
      throw new IllegalArgumentException("Practice code review language must not be blank");
    }
    if (score == null) {
      throw new IllegalArgumentException("Practice code review score must not be null");
    }
    problemSlug = problemSlug.trim();
    rawCode = rawCode.strip();
    normalizedCode = normalizedCode == null || normalizedCode.isBlank() ? rawCode : normalizedCode.strip();
    language = language.trim();
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    contextSummary = blankToNull(contextSummary);
    deductionReasons = copyTrimmed(deductionReasons);
    improvementSuggestions = copyTrimmed(improvementSuggestions);
    reviewMarkdown = blankToNull(reviewMarkdown);
  }

  private static void requirePositive(long value, String fieldName) {
    if (value < 1) {
      throw new IllegalArgumentException("Practice code review " + fieldName + " must be positive");
    }
  }

  private static void requirePositiveIfPresent(Long value, String fieldName) {
    if (value != null && value < 1) {
      throw new IllegalArgumentException("Practice code review " + fieldName + " must be positive");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static List<String> copyTrimmed(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream()
        .map(PracticeCodeReviewDraft::blankToNull)
        .filter(value -> value != null)
        .toList();
  }
}
