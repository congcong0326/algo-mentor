package org.congcong.algomentor.mentor.application.learningplan;

import java.util.List;

public record LearningPlanLoadSummary(
    int durationWeeks,
    int weeklyHours,
    double weeklyCapacityPoints,
    double totalCapacityPoints,
    double plannedLoadPoints,
    double loadRatio,
    int plannedProblemCount,
    double averageProblemsPerWeek,
    String intensity,
    boolean reviewBufferIncluded,
    List<String> suggestions
) {

  public LearningPlanLoadSummary {
    suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
  }
}
