package org.congcong.algomentor.mentor.application.profile.review.history;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;

/** 供纵向轨迹计算使用的正式 Review 历史，不包含代码和完整 Markdown。 */
public record CodeReviewHistory(
    long reviewId,
    String problemSlug,
    int versionNo,
    PracticeCodeReviewScore score,
    boolean passed,
    List<String> deductionReasons,
    List<String> improvementSuggestions,
    List<Long> affectedTagIds,
    Instant createdAt
) {

  public CodeReviewHistory {
    if (reviewId < 1 || problemSlug == null || problemSlug.isBlank() || versionNo < 1 || score == null
        || createdAt == null) {
      throw new IllegalArgumentException("Invalid code review history");
    }
    problemSlug = problemSlug.trim();
    deductionReasons = normalizedStrings(deductionReasons);
    improvementSuggestions = normalizedStrings(improvementSuggestions);
    affectedTagIds = normalizedIds(affectedTagIds);
  }

  public BigDecimal totalScore() {
    return score.total();
  }

  private static List<String> normalizedStrings(List<String> values) {
    return values == null ? List.of() : values.stream()
        .filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
  }

  private static List<Long> normalizedIds(List<Long> values) {
    return values == null ? List.of() : values.stream()
        .filter(value -> value != null && value > 0).distinct().sorted().toList();
  }
}
