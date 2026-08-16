package org.congcong.algomentor.mentor.application.practice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 一条正式代码 Review 在历史提交 Tool 中允许读取的受限事实。 */
public record PracticeSubmissionHistoryReview(
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
    List<String> deductionReasons,
    List<String> improvementSuggestions,
    List<Long> affectedTagIds,
    String reviewHistorySummary
) {

  public PracticeSubmissionHistoryReview {
    if (reviewId < 1 || submittedAt == null || language == null || language.isBlank() || totalScore == null
        || correctnessScore == null || complexityScore == null || edgeCaseScore == null || codeQualityScore == null
        || problemFitScore == null) {
      throw new IllegalArgumentException("Practice submission history review is invalid");
    }
    language = language.trim();
    deductionReasons = cleanedText(deductionReasons);
    improvementSuggestions = cleanedText(improvementSuggestions);
    affectedTagIds = affectedTagIds == null ? List.of() : affectedTagIds.stream()
        .filter(tagId -> tagId != null && tagId > 0)
        .distinct()
        .toList();
    reviewHistorySummary = normalizeSummary(reviewHistorySummary);
  }

  private static List<String> cleanedText(List<String> values) {
    return values == null ? List.of() : values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(value -> value.replaceAll("\\s+", " ").trim())
        .toList();
  }

  private static String normalizeSummary(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.replaceAll("\\s+", " ").trim();
    return normalized.length() <= PracticeCodeReviewConstants.REVIEW_HISTORY_SUMMARY_MAX_LENGTH ? normalized : null;
  }
}
