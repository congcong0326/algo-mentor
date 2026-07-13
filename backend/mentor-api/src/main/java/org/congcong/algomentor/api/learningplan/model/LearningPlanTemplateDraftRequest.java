package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftCommand;

public record LearningPlanTemplateDraftRequest(
    String templateId,
    String programmingLanguage,
    Integer dailyProblemCount,
    Integer trainingDaysPerWeek
) {

  public LearningPlanTemplateDraftCommand toCommand() {
    return toCommand(null);
  }

  public LearningPlanTemplateDraftCommand toCommand(String recommendationReasonLocale) {
    return new LearningPlanTemplateDraftCommand(
        templateId,
        programmingLanguage,
        dailyProblemCount,
        trainingDaysPerWeek,
        recommendationReasonLocale);
  }
}
