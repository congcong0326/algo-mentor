package org.congcong.algomentor.mentor.application.learningplan;

import java.time.LocalDate;
import java.util.List;

public record TodayPack(
    TodayPackState state,
    LocalDate localDate,
    String timezone,
    int packOffset,
    TodayPackActivePlan activePlan,
    List<TodayPackSection> sections,
    String notice,
    TodayPackRecommendedPlan recommendedPlan,
    LocalDate nextPackDate
) {

  public TodayPack {
    sections = sections == null ? List.of() : List.copyOf(sections);
  }
}
