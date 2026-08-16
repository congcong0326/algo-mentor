package org.congcong.algomentor.mentor.application.review.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/** 将日级复习安排到用户时区内目标日期的零点。 */
public class ReviewDayBoundary {

  public Instant dueAt(Instant now, int intervalDays, ZoneId userZone) {
    Objects.requireNonNull(now, "now must not be null");
    Objects.requireNonNull(userZone, "userZone must not be null");
    if (intervalDays <= 0) {
      throw new IllegalArgumentException("intervalDays must be positive");
    }
    return now.atZone(userZone)
        .toLocalDate()
        .plusDays(intervalDays)
        .atStartOfDay(userZone)
        .toInstant();
  }
}
