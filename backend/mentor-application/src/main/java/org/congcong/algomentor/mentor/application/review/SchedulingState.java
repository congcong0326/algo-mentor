package org.congcong.algomentor.mentor.application.review;

import java.math.BigDecimal;

public record SchedulingState(
    int repetitions,
    BigDecimal easeFactor,
    int intervalDays,
    MasteryState masteryState,
    int lapses,
    String fsrsState,
    Integer fsrsStep,
    BigDecimal fsrsStability,
    BigDecimal fsrsDifficulty
) {
  public SchedulingState(
      int repetitions,
      BigDecimal easeFactor,
      int intervalDays,
      MasteryState masteryState,
      int lapses
  ) {
    this(repetitions, easeFactor, intervalDays, masteryState, lapses, "LEARNING", 0, null, null);
  }

  public SchedulingState {
    if (easeFactor == null) {
      easeFactor = new BigDecimal("2.50");
    }
    if (masteryState == null) {
      masteryState = MasteryState.NEW;
    }
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
