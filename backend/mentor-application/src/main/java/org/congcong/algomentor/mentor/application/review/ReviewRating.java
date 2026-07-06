package org.congcong.algomentor.mentor.application.review;

import io.github.openspacedrepetition.Rating;

/**
 * Anki/FSRS 风格的复习最终评级。
 */
public enum ReviewRating {
  AGAIN(Rating.AGAIN),
  HARD(Rating.HARD),
  GOOD(Rating.GOOD),
  EASY(Rating.EASY);

  private final Rating fsrsRating;

  ReviewRating(Rating fsrsRating) {
    this.fsrsRating = fsrsRating;
  }

  public Rating fsrsRating() {
    return fsrsRating;
  }

  public static ReviewRating parse(String value) {
    if (value == null || value.isBlank()) {
      throw new MistakeReviewException("REVIEW_RATING_REQUIRED", "复习评级不能为空。");
    }
    try {
      return ReviewRating.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new MistakeReviewException("REVIEW_RATING_INVALID", "复习评级不合法。");
    }
  }
}
