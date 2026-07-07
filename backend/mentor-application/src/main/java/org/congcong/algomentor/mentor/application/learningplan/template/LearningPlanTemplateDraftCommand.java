package org.congcong.algomentor.mentor.application.learningplan.template;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRhythmMode;

public record LearningPlanTemplateDraftCommand(
    String templateId,
    Integer durationWeeks,
    Integer weeklyHours,
    String programmingLanguage,
    LearningPlanRhythmMode rhythmMode
) {

  public LearningPlanTemplateDraftCommand(
      String templateId,
      Integer durationWeeks,
      Integer weeklyHours,
      String programmingLanguage
  ) {
    this(templateId, durationWeeks, weeklyHours, programmingLanguage, null);
  }

  public LearningPlanTemplateDraftCommand {
    templateId = templateId == null ? null : templateId.trim();
    programmingLanguage = programmingLanguage == null || programmingLanguage.isBlank()
        ? null
        : programmingLanguage.trim();
  }
}
