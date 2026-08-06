package org.congcong.algomentor.mentor.application.learningplan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LearningPlanPhaseDraft(
    int phaseIndex,
    String title,
    int durationWeeks,
    String focus,
    List<LearningPlanProblemDraft> problems
) {

  public LearningPlanPhaseDraft {
    problems = problems == null ? List.of() : List.copyOf(problems);
  }
}
