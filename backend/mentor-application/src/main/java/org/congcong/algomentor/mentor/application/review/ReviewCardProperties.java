package org.congcong.algomentor.mentor.application.review;

import java.time.Duration;

public record ReviewCardProperties(
    int dailyLimit,
    Duration cacheTtl,
    int prefetchCount
) {
  public ReviewCardProperties {
    if (dailyLimit < 0) {
      dailyLimit = 20;
    }
    if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
      cacheTtl = Duration.ofHours(168);
    }
    if (prefetchCount < 0) {
      prefetchCount = 3;
    }
  }

  public static ReviewCardProperties defaults() {
    return new ReviewCardProperties(20, Duration.ofHours(168), 3);
  }
}
