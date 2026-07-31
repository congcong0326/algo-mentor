package org.congcong.algomentor.mentor.application.profile.review;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 供画像模型使用的最小正式 Review 事实，不含代码、证据或完整 Markdown。 */
public record LearnerMemoryCodeReviewFact(
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
    List<String> deductionReasons,
    List<String> improvementSuggestions,
    List<Long> affectedTagIds,
    Instant createdAt
) {
  public LearnerMemoryCodeReviewFact {
    if (reviewId < 1 || problemSlug == null || problemSlug.isBlank() || versionNo < 1
        || totalScore == null || correctnessScore == null || complexityScore == null || edgeCaseScore == null
        || codeQualityScore == null || problemFitScore == null || createdAt == null) {
      throw new IllegalArgumentException("Invalid code review profile fact");
    }
    problemSlug = problemSlug.trim();
    deductionReasons = normalizedStrings(deductionReasons);
    improvementSuggestions = normalizedStrings(improvementSuggestions);
    affectedTagIds = affectedTagIds == null ? List.of() : affectedTagIds.stream()
        .filter(tagId -> tagId != null && tagId > 0).distinct().sorted().toList();
  }

  private static List<String> normalizedStrings(List<String> values) {
    return values == null ? List.of() : values.stream()
        .filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
  }
}
