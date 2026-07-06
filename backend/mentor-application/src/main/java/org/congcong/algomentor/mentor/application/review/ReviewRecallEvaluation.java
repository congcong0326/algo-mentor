package org.congcong.algomentor.mentor.application.review;

import java.time.Instant;
import java.util.List;

public record ReviewRecallEvaluation(
    long id,
    long noteId,
    long userId,
    String recallText,
    String transientNote,
    ReviewRating suggestedRating,
    List<String> hitPoints,
    List<String> missedPoints,
    String gapSummary,
    boolean aiSuggested,
    Instant createdAt
) {
  public ReviewRecallEvaluation {
    hitPoints = hitPoints == null ? List.of() : List.copyOf(hitPoints);
    missedPoints = missedPoints == null ? List.of() : List.copyOf(missedPoints);
    gapSummary = gapSummary == null ? "" : gapSummary;
  }
}
