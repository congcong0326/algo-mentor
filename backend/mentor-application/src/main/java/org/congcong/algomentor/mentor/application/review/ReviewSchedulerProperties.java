package org.congcong.algomentor.mentor.application.review;

public record ReviewSchedulerProperties(
    int graduationIntervalDays,
    int graduationRepetitions
) {
  public ReviewSchedulerProperties {
    if (graduationIntervalDays <= 0) {
      graduationIntervalDays = 30;
    }
    if (graduationRepetitions <= 0) {
      graduationRepetitions = 3;
    }
  }

  public static ReviewSchedulerProperties defaults() {
    return new ReviewSchedulerProperties(30, 3);
  }
}
