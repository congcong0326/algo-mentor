package org.congcong.algomentor.mentor.application.learningplan.template;

public record LearningPlanTemplateDraftCommand(
    String templateId,
    String programmingLanguage,
    Integer dailyProblemCount,
    Integer trainingDaysPerWeek
) {

  public LearningPlanTemplateDraftCommand(
      String templateId,
      String programmingLanguage
  ) {
    this(templateId, programmingLanguage, null, null);
  }

  public LearningPlanTemplateDraftCommand {
    templateId = templateId == null ? null : templateId.trim();
    programmingLanguage = programmingLanguage == null || programmingLanguage.isBlank()
        ? null
        : programmingLanguage.trim();
  }
}
