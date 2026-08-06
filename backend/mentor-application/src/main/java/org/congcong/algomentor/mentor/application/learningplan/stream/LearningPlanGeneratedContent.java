package org.congcong.algomentor.mentor.application.learningplan.stream;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;

/** 初次学习计划生成中仅允许模型决定的内容。 */
public record LearningPlanGeneratedContent(
    String title,
    String summary,
    List<LearningPlanPhaseDraft> phases
) {

  public LearningPlanGeneratedContent {
    phases = phases == null ? List.of() : List.copyOf(phases);
  }
}
