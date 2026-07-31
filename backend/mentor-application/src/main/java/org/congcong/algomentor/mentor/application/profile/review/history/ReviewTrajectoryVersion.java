package org.congcong.algomentor.mentor.application.profile.review.history;

import java.math.BigDecimal;
import java.util.List;

/** 单一版本相对于其相邻前版的得分与 finding 变化。 */
public record ReviewTrajectoryVersion(
    CodeReviewHistory review,
    BigDecimal scoreDelta,
    List<String> persistedFindings,
    List<String> resolvedFindings,
    List<String> newFindings
) {

  public ReviewTrajectoryVersion {
    if (review == null) {
      throw new IllegalArgumentException("Review trajectory version review must not be null");
    }
    persistedFindings = stable(persistedFindings);
    resolvedFindings = stable(resolvedFindings);
    newFindings = stable(newFindings);
  }

  private static List<String> stable(List<String> values) {
    return values == null ? List.of() : values.stream().distinct().sorted().toList();
  }
}
