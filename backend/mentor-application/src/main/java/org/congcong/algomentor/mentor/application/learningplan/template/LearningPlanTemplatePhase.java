package org.congcong.algomentor.mentor.application.learningplan.template;

import java.util.List;

public record LearningPlanTemplatePhase(
    Long id,
    int phaseIndex,
    String title,
    int durationWeeks,
    String focus,
    List<String> objectives,
    List<String> recommendedTags,
    List<String> acceptanceCriteria,
    String reviewAdvice,
    List<LearningPlanTemplateProblemRef> problemRefs
) {

  public LearningPlanTemplatePhase {
    objectives = objectives == null ? List.of() : List.copyOf(objectives);
    recommendedTags = recommendedTags == null ? List.of() : List.copyOf(recommendedTags);
    acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    problemRefs = problemRefs == null ? List.of() : List.copyOf(problemRefs);
  }

  public LearningPlanTemplatePhase withProblemRefs(List<LearningPlanTemplateProblemRef> nextProblemRefs) {
    return new LearningPlanTemplatePhase(
        id,
        phaseIndex,
        title,
        durationWeeks,
        focus,
        objectives,
        recommendedTags,
        acceptanceCriteria,
        reviewAdvice,
        nextProblemRefs);
  }
}
