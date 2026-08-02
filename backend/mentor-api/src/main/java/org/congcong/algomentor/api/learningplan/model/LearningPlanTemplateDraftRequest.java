package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftCommand;

public record LearningPlanTemplateDraftRequest(
    String templateId,
    String programmingLanguage,
    Integer dailyProblemCount,
    Integer trainingDaysPerWeek,
    LearningPlanContentLocale contentLocale
) {

  public LearningPlanTemplateDraftCommand toCommand() {
    return toCommand(LearningPlanContentLocale.ZH_CN);
  }

  public LearningPlanTemplateDraftCommand toCommand(String recommendationReasonLocale) {
    return toCommand(LearningPlanContentLocale.fromValue(recommendationReasonLocale));
  }

  public LearningPlanTemplateDraftCommand toCommand(LearningPlanContentLocale fallbackLocale) {
    return new LearningPlanTemplateDraftCommand(
        templateId,
        programmingLanguage,
        dailyProblemCount,
        trainingDaysPerWeek,
        contentLocale == null ? fallbackLocale : contentLocale);
  }
}
