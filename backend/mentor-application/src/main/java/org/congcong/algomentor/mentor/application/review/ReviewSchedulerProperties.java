package org.congcong.algomentor.mentor.application.review;

import java.math.BigDecimal;

public record ReviewSchedulerProperties(
    int graduationIntervalDays,
    int graduationRepetitions,
    int passedFirstIntervalDays,
    int passedHighScoreIntervalDays,
    int lowConfidenceIntervalDays,
    BigDecimal highScoreRatio,
    int queueDailyCap
) {
  public ReviewSchedulerProperties {
    if (graduationIntervalDays <= 0) {
      graduationIntervalDays = 30;
    }
    if (graduationRepetitions <= 0) {
      graduationRepetitions = 3;
    }
    if (passedFirstIntervalDays <= 0) {
      passedFirstIntervalDays = 3;
    }
    if (passedHighScoreIntervalDays <= 0) {
      passedHighScoreIntervalDays = 4;
    }
    if (lowConfidenceIntervalDays <= 0) {
      lowConfidenceIntervalDays = 1;
    }
    if (highScoreRatio == null || highScoreRatio.compareTo(BigDecimal.ZERO) <= 0) {
      highScoreRatio = new BigDecimal("0.90");
    }
    if (queueDailyCap <= 0) {
      queueDailyCap = 20;
    }
  }

  public ReviewSchedulerProperties(int graduationIntervalDays, int graduationRepetitions) {
    this(graduationIntervalDays, graduationRepetitions, 3, 4, 1, new BigDecimal("0.90"), 20);
  }

  public static ReviewSchedulerProperties defaults() {
    return new ReviewSchedulerProperties(30, 3);
  }
}
