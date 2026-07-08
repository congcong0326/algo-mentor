package org.congcong.algomentor.api.learningplan.model;

import java.time.LocalDate;
import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackState;

public record TodayPackResponse(
    TodayPackState state,
    LocalDate localDate,
    String timezone,
    int packOffset,
    TodayPackActivePlanResponse activePlan,
    List<TodayPackSectionResponse> sections,
    String notice,
    TodayPackRecommendedPlanResponse recommendedPlan,
    LocalDate nextPackDate
) {

  public TodayPackResponse {
    sections = sections == null ? List.of() : List.copyOf(sections);
  }
}
