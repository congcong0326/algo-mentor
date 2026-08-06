package org.congcong.algomentor.mentor.application.learningplan.template;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;

public record LearningPlanTemplatePhase(
    Long id,
    int phaseIndex,
    String title,
    String titleEn,
    int durationWeeks,
    String focus,
    String focusEn,
    List<LearningPlanTemplateProblemRef> problemRefs
) {

  public LearningPlanTemplatePhase {
    problemRefs = problemRefs == null ? List.of() : List.copyOf(problemRefs);
  }

  public String title(LearningPlanContentLocale locale) {
    return locale == LearningPlanContentLocale.EN_US ? titleEn : title;
  }

  public String focus(LearningPlanContentLocale locale) {
    return locale == LearningPlanContentLocale.EN_US ? focusEn : focus;
  }

  public LearningPlanTemplatePhase withProblemRefs(List<LearningPlanTemplateProblemRef> nextProblemRefs) {
    return new LearningPlanTemplatePhase(
        id,
        phaseIndex,
        title,
        titleEn,
        durationWeeks,
        focus,
        focusEn,
        nextProblemRefs);
  }
}
