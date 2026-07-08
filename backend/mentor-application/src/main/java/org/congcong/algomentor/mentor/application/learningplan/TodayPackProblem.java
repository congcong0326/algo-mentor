package org.congcong.algomentor.mentor.application.learningplan;

import java.time.LocalDate;
import java.util.List;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;

public record TodayPackProblem(
    long planId,
    int phaseIndex,
    String slug,
    Integer frontendId,
    String title,
    String titleCn,
    String difficulty,
    List<String> tags,
    PracticeProgressStatus progressStatus,
    LocalDate scheduledDate,
    long carryoverDays
) {

  public TodayPackProblem {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }
}
