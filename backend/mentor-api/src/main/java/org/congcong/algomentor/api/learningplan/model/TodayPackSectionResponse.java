package org.congcong.algomentor.api.learningplan.model;

import java.time.LocalDate;
import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.TodayPackSectionType;

public record TodayPackSectionResponse(
    TodayPackSectionType type,
    String title,
    LocalDate date,
    List<TodayPackProblemResponse> problems
) {

  public TodayPackSectionResponse {
    problems = problems == null ? List.of() : List.copyOf(problems);
  }
}
