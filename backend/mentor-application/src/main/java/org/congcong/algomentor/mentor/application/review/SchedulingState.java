package org.congcong.algomentor.mentor.application.review;

import java.math.BigDecimal;

public record SchedulingState(
    int repetitions,
    int intervalDays,
    int lapses,
    String fsrsState,
    Integer fsrsStep,
    BigDecimal fsrsStability,
    BigDecimal fsrsDifficulty
) {
  public SchedulingState(
      int repetitions,
      int intervalDays,
      int lapses
  ) {
    this(repetitions, intervalDays, lapses, "LEARNING", 0, null, null);
  }

  public SchedulingState {
    if (fsrsState == null || fsrsState.isBlank()) {
      fsrsState = "LEARNING";
    }
    if (fsrsStability != null) {
      fsrsStability = fsrsStability.stripTrailingZeros();
    }
    if (fsrsDifficulty != null) {
      fsrsDifficulty = fsrsDifficulty.stripTrailingZeros();
    }
  }
}
