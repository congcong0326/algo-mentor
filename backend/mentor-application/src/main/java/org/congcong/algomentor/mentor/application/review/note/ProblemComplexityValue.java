package org.congcong.algomentor.mentor.application.review.note;

import org.congcong.algomentor.mentor.application.review.ReviewException;

public record ProblemComplexityValue(ProblemComplexityKey key, String customText) {
  public ProblemComplexityValue {
    customText = normalize(customText);
    if (key == ProblemComplexityKey.OTHER && customText == null) {
      throw new ReviewException(
          "PROBLEM_NOTE_CUSTOM_COMPLEXITY_REQUIRED",
          "复杂度选择 OTHER 时必须填写自定义复杂度。");
    }
    if (key != ProblemComplexityKey.OTHER) {
      customText = null;
    }
  }

  public static ProblemComplexityValue empty() {
    return new ProblemComplexityValue(null, null);
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
