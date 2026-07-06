package org.congcong.algomentor.mentor.application.learningplan.template;

import java.util.Map;

public record LearningPlanTemplateProblemRef(
    Long id,
    int phaseIndex,
    int sortOrder,
    int sourceOrder,
    String problemSlug,
    String sourceTitle,
    String sourceDifficulty,
    String pattern,
    String sourceUrl,
    boolean matchedProblem,
    Map<String, Object> metadata
) {

  public LearningPlanTemplateProblemRef {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public LearningPlanTemplateProblemRef withMatchedProblem(boolean nextMatchedProblem) {
    return new LearningPlanTemplateProblemRef(
        id,
        phaseIndex,
        sortOrder,
        sourceOrder,
        problemSlug,
        sourceTitle,
        sourceDifficulty,
        pattern,
        sourceUrl,
        nextMatchedProblem,
        metadata);
  }
}
