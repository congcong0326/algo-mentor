package org.congcong.algomentor.mentor.application.review.schedule;

import io.github.openspacedrepetition.Rating;
import java.util.Locale;
import org.congcong.algomentor.mentor.application.review.ReviewException;

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
      throw new ReviewException("REVIEW_RATING_REQUIRED", "复习评级不能为空。");
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new ReviewException("REVIEW_RATING_INVALID", "复习评级不合法。");
    }
  }
}
