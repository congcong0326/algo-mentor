package org.congcong.algomentor.api.activity.model;

import java.time.LocalDate;
import java.util.List;

public record ActivityContributionResponse(
    String timezone,
    LocalDate from,
    LocalDate to,
    long totalCount,
    int activeDays,
    int currentStreak,
    int longestStreak,
    List<ActivityContributionDayResponse> days
) {

  public ActivityContributionResponse {
    days = days == null ? List.of() : List.copyOf(days);
  }
}
