package org.congcong.algomentor.mentor.application.review;

public enum ReviewGrade {
  FORGOT(2),
  BARELY(3),
  MASTERED(4),
  FLUENT(5);

  private final int q;

  ReviewGrade(int q) {
    this.q = q;
  }

  public int q() {
    return q;
  }

  public static ReviewGrade ofQ(int q) {
    if (q <= FORGOT.q) {
      return FORGOT;
    }
    if (q == BARELY.q) {
      return BARELY;
    }
    if (q == MASTERED.q) {
      return MASTERED;
    }
    return FLUENT;
  }
}
