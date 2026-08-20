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
    List<ActivityContributionDailyCountResponse> dailyCounts
) {

  public ActivityContributionResponse {
    dailyCounts = dailyCounts == null ? List.of() : List.copyOf(dailyCounts);
  }
}
