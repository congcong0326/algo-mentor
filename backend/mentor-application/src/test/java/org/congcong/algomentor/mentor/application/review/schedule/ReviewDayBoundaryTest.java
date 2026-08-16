package org.congcong.algomentor.mentor.application.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ReviewDayBoundaryTest {

  private final ReviewDayBoundary boundary = new ReviewDayBoundary();

  @Test
  void schedulesTheNextReviewAtTheUsersNextMidnightAcrossDstStart() {
    Instant now = Instant.parse("2026-03-08T18:00:00Z");

    Instant dueAt = boundary.dueAt(now, 1, ZoneId.of("America/Los_Angeles"));

    assertThat(dueAt).isEqualTo(Instant.parse("2026-03-09T07:00:00Z"));
  }

  @Test
  void usesTheLocalCalendarDateInsteadOfAddingTwentyFourHours() {
    Instant now = Instant.parse("2026-11-01T19:00:00Z");

    Instant dueAt = boundary.dueAt(now, 1, ZoneId.of("America/Los_Angeles"));

    assertThat(dueAt).isEqualTo(Instant.parse("2026-11-02T08:00:00Z"));
  }
}
