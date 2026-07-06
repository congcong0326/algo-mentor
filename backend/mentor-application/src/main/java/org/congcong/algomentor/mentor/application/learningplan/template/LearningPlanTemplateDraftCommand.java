package org.congcong.algomentor.mentor.application.learningplan.template;

public record LearningPlanTemplateDraftCommand(
    String templateId,
    Integer durationWeeks,
    Integer weeklyHours,
    String programmingLanguage
) {

  public LearningPlanTemplateDraftCommand {
    templateId = templateId == null ? null : templateId.trim();
    programmingLanguage = programmingLanguage == null || programmingLanguage.isBlank()
        ? null
        : programmingLanguage.trim();
  }
}
