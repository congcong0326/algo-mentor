package org.congcong.algomentor.mentor.application.learningplan.template;

import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;

public record LearningPlanTemplateDraftCommand(
    String templateId,
    String programmingLanguage,
    Integer dailyProblemCount,
    Integer trainingDaysPerWeek,
    LearningPlanContentLocale contentLocale
) {

  /** 默认使用中文题库推荐理由。 */
  public static final String DEFAULT_RECOMMENDATION_REASON_LOCALE = "zh-CN";

  /** 英文题库推荐理由的规范语言值。 */
  public static final String ENGLISH_RECOMMENDATION_REASON_LOCALE = "en-US";

  public LearningPlanTemplateDraftCommand(
      String templateId,
      String programmingLanguage,
      Integer dailyProblemCount,
      Integer trainingDaysPerWeek,
      String contentLocale
  ) {
    this(
        templateId,
        programmingLanguage,
        dailyProblemCount,
        trainingDaysPerWeek,
        LearningPlanContentLocale.fromValue(contentLocale));
  }

  public LearningPlanTemplateDraftCommand(
      String templateId,
      String programmingLanguage,
      Integer dailyProblemCount,
      Integer trainingDaysPerWeek
  ) {
    this(
        templateId,
        programmingLanguage,
        dailyProblemCount,
        trainingDaysPerWeek,
        LearningPlanContentLocale.ZH_CN);
  }

  public LearningPlanTemplateDraftCommand(
      String templateId,
      String programmingLanguage
  ) {
    this(templateId, programmingLanguage, null, null, LearningPlanContentLocale.ZH_CN);
  }

  public LearningPlanTemplateDraftCommand {
    templateId = templateId == null ? null : templateId.trim();
    programmingLanguage = programmingLanguage == null || programmingLanguage.isBlank()
        ? null
        : programmingLanguage.trim();
    contentLocale = contentLocale == null ? LearningPlanContentLocale.ZH_CN : contentLocale;
  }

  public String recommendationReasonLocale() {
    return contentLocale.languageTag();
  }
}
