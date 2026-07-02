package org.congcong.algomentor.mentor.application.review;

import java.math.BigDecimal;

public record SchedulingState(
    int repetitions,
    BigDecimal easeFactor,
    int intervalDays,
    MasteryState masteryState,
    int lapses
) {
  public SchedulingState {
    if (easeFactor == null) {
      easeFactor = new BigDecimal("2.50");
    }
    if (masteryState == null) {
      masteryState = MasteryState.NEW;
    }
  }
}
