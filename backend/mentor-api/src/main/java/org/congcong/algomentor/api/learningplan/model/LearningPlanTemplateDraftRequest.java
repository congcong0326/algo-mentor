package org.congcong.algomentor.api.learningplan.model;

import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateDraftCommand;

public record LearningPlanTemplateDraftRequest(
    String templateId,
    Integer durationWeeks,
    Integer weeklyHours,
    String programmingLanguage
) {

  public LearningPlanTemplateDraftCommand toCommand() {
    return new LearningPlanTemplateDraftCommand(templateId, durationWeeks, weeklyHours, programmingLanguage);
  }
}
