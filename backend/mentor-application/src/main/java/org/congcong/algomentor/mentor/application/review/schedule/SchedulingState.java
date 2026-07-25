package org.congcong.algomentor.mentor.application.review.schedule;

import java.math.BigDecimal;

public record SchedulingState(
    int repetitions,
    int intervalDays,
    int lapses,
    FsrsState fsrsState,
    Integer fsrsStep,
    BigDecimal fsrsStability,
    BigDecimal fsrsDifficulty
) {
  public SchedulingState {
    repetitions = Math.max(0, repetitions);
    intervalDays = Math.max(0, intervalDays);
    lapses = Math.max(0, lapses);
    fsrsState = fsrsState == null ? FsrsState.LEARNING : fsrsState;
    if (fsrsStability != null) {
      fsrsStability = fsrsStability.stripTrailingZeros();
    }
    if (fsrsDifficulty != null) {
      fsrsDifficulty = fsrsDifficulty.stripTrailingZeros();
    }
  }

  public static SchedulingState initial() {
    return new SchedulingState(0, 0, 0, FsrsState.LEARNING, 0, null, null);
  }
}
