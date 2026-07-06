package org.congcong.algomentor.mentor.application.review;

import java.util.List;

public record RecallJudgment(
    ReviewRating suggestedRating,
    List<String> hitPoints,
    List<String> missedPoints,
    String gapSummary
) {
  public RecallJudgment {
    if (suggestedRating == null) {
      suggestedRating = ReviewRating.HARD;
    }
    hitPoints = hitPoints == null ? List.of() : List.copyOf(hitPoints);
    missedPoints = missedPoints == null ? List.of() : List.copyOf(missedPoints);
    gapSummary = gapSummary == null ? "" : gapSummary;
  }
}
