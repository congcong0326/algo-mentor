package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplatePhase;

public final class LearningPlanTemplateResponseMapper {

  public static LearningPlanTemplateSummaryResponse toSummaryResponse(LearningPlanTemplate template) {
    return toSummaryResponse(template, new LearningPlanLoadService(), LearningPlanContentLocale.ZH_CN);
  }

  public static LearningPlanTemplateSummaryResponse toSummaryResponse(
      LearningPlanTemplate template,
      LearningPlanLoadService loadService
  ) {
    return toSummaryResponse(template, loadService, LearningPlanContentLocale.ZH_CN);
  }

  public static LearningPlanTemplateSummaryResponse toSummaryResponse(
      LearningPlanTemplate template,
      LearningPlanLoadService loadService,
      LearningPlanContentLocale requestedLocale
  ) {
    LearningPlanContentLocale contentLocale = template.resolveContentLocale(requestedLocale);
    return new LearningPlanTemplateSummaryResponse(
        template.templateId(),
        contentLocale,
        template.title(contentLocale),
        template.summary(contentLocale),
        template.catalogCategory(),
        template.recommendedOrder(),
        template.intent(),
        template.defaultDurationWeeks(),
        template.level(),
        template.defaultWeeklyHours(),
        template.programmingLanguage(),
        template.difficultyPreference(),
        template.topicPreferences(),
        template.targetAudience(contentLocale),
        template.expectedOutcome(contentLocale),
        template.matchedProblemCount(),
        loadService.defaultLoadSummary(template),
        loadService.defaultRhythmSettings(template));
  }

  public static LearningPlanTemplateDetailResponse toDetailResponse(LearningPlanTemplate template) {
    return toDetailResponse(template, new LearningPlanLoadService(), LearningPlanContentLocale.ZH_CN);
  }

  public static LearningPlanTemplateDetailResponse toDetailResponse(
      LearningPlanTemplate template,
      LearningPlanLoadService loadService
  ) {
    return toDetailResponse(template, loadService, LearningPlanContentLocale.ZH_CN);
  }

  public static LearningPlanTemplateDetailResponse toDetailResponse(
      LearningPlanTemplate template,
      LearningPlanLoadService loadService,
      LearningPlanContentLocale requestedLocale
  ) {
    LearningPlanContentLocale contentLocale = template.resolveContentLocale(requestedLocale);
    return new LearningPlanTemplateDetailResponse(
        template.templateId(),
        contentLocale,
        template.title(contentLocale),
        template.summary(contentLocale),
        template.catalogCategory(),
        template.recommendedOrder(),
        template.intent(),
        template.goal(contentLocale),
        template.defaultDurationWeeks(),
        template.level(),
        template.defaultWeeklyHours(),
        template.programmingLanguage(),
        template.difficultyPreference(),
        template.topicPreferences(),
        template.targetAudience(contentLocale),
        template.prerequisites(contentLocale),
        template.recommendedFor(contentLocale),
        template.notRecommendedFor(contentLocale),
        template.expectedOutcome(contentLocale),
        template.sourceName(),
        template.sourceUrl(),
        template.matchedProblemCount(),
        loadService.defaultLoadSummary(template),
        loadService.defaultRhythmSettings(template),
        template.phases().stream().map(phase -> toPhaseResponse(phase, contentLocale)).toList());
  }

  private static LearningPlanTemplatePhaseResponse toPhaseResponse(
      LearningPlanTemplatePhase phase,
      LearningPlanContentLocale contentLocale
  ) {
    return new LearningPlanTemplatePhaseResponse(
        phase.phaseIndex(),
        phase.title(contentLocale),
        phase.durationWeeks(),
        phase.focus(contentLocale),
        (int) phase.problemRefs().stream().filter(ref -> ref.matchedProblem()).count());
  }

  public static List<LearningPlanTemplateSummaryResponse> toSummaryResponses(List<LearningPlanTemplate> templates) {
    return toSummaryResponses(templates, new LearningPlanLoadService());
  }

  public static List<LearningPlanTemplateSummaryResponse> toSummaryResponses(
      List<LearningPlanTemplate> templates,
      LearningPlanLoadService loadService
  ) {
    return toSummaryResponses(templates, loadService, LearningPlanContentLocale.ZH_CN);
  }

  public static List<LearningPlanTemplateSummaryResponse> toSummaryResponses(
      List<LearningPlanTemplate> templates,
      LearningPlanLoadService loadService,
      LearningPlanContentLocale requestedLocale
  ) {
    return templates.stream()
        .map(template -> LearningPlanTemplateResponseMapper.toSummaryResponse(
            template,
            loadService,
            requestedLocale))
        .toList();
  }

  private LearningPlanTemplateResponseMapper() {
  }
}
