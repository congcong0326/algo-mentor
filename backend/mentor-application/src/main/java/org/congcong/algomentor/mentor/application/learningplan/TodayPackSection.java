package org.congcong.algomentor.mentor.application.learningplan;

import java.time.LocalDate;
import java.util.List;

public record TodayPackSection(
    TodayPackSectionType type,
    String title,
    LocalDate date,
    List<TodayPackProblem> problems
) {

  public TodayPackSection {
    problems = problems == null ? List.of() : List.copyOf(problems);
  }
}
