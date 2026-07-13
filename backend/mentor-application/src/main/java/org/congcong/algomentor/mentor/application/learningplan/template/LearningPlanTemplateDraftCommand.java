package org.congcong.algomentor.mentor.application.learningplan.template;

import java.util.Locale;

public record LearningPlanTemplateDraftCommand(
    String templateId,
    String programmingLanguage,
    Integer dailyProblemCount,
    Integer trainingDaysPerWeek,
    String recommendationReasonLocale
) {

  /** 默认使用中文题库推荐理由。 */
  public static final String DEFAULT_RECOMMENDATION_REASON_LOCALE = "zh-CN";

  /** 英文题库推荐理由的规范语言值。 */
  public static final String ENGLISH_RECOMMENDATION_REASON_LOCALE = "en-US";

  public LearningPlanTemplateDraftCommand(
      String templateId,
      String programmingLanguage,
      Integer dailyProblemCount,
      Integer trainingDaysPerWeek
  ) {
    this(templateId, programmingLanguage, dailyProblemCount, trainingDaysPerWeek, null);
  }

  public LearningPlanTemplateDraftCommand(
      String templateId,
      String programmingLanguage
  ) {
    this(templateId, programmingLanguage, null, null, null);
  }

  public LearningPlanTemplateDraftCommand {
    templateId = templateId == null ? null : templateId.trim();
    programmingLanguage = programmingLanguage == null || programmingLanguage.isBlank()
        ? null
        : programmingLanguage.trim();
    recommendationReasonLocale = normalizeRecommendationReasonLocale(recommendationReasonLocale);
  }

  private static String normalizeRecommendationReasonLocale(String locale) {
    if (locale == null || locale.isBlank()) {
      return DEFAULT_RECOMMENDATION_REASON_LOCALE;
    }
    return locale.trim().toLowerCase(Locale.ROOT).startsWith("en")
        ? ENGLISH_RECOMMENDATION_REASON_LOCALE
        : DEFAULT_RECOMMENDATION_REASON_LOCALE;
  }
}
