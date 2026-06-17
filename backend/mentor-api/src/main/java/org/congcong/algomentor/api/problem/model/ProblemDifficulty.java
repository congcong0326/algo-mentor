package org.congcong.algomentor.api.problem.model;

import java.util.Locale;

public enum ProblemDifficulty {
  EASY,
  MEDIUM,
  HARD;

  public static ProblemDifficulty parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return ProblemDifficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
  }
}
