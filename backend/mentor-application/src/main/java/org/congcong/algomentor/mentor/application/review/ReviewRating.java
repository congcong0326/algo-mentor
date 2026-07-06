package org.congcong.algomentor.mentor.application.review;

import io.github.openspacedrepetition.Rating;

/**
 * Anki/FSRS 风格的复习最终评级。
 */
public enum ReviewRating {
  AGAIN(Rating.AGAIN, ReviewGrade.FORGOT),
  HARD(Rating.HARD, ReviewGrade.BARELY),
  GOOD(Rating.GOOD, ReviewGrade.MASTERED),
  EASY(Rating.EASY, ReviewGrade.FLUENT);

  private final Rating fsrsRating;
  private final ReviewGrade legacyGrade;

  ReviewRating(Rating fsrsRating, ReviewGrade legacyGrade) {
    this.fsrsRating = fsrsRating;
    this.legacyGrade = legacyGrade;
  }

  public Rating fsrsRating() {
    return fsrsRating;
  }

  public ReviewGrade legacyGrade() {
    return legacyGrade;
  }

  public static ReviewRating fromGrade(ReviewGrade grade) {
    if (grade == null) {
      return HARD;
    }
    return switch (grade) {
      case FORGOT -> AGAIN;
      case BARELY -> HARD;
      case MASTERED -> GOOD;
      case FLUENT -> EASY;
    };
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
