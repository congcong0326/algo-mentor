package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplatePhase;

public final class LearningPlanTemplateResponseMapper {

  public static LearningPlanTemplateSummaryResponse toSummaryResponse(LearningPlanTemplate template) {
    return toSummaryResponse(template, new LearningPlanLoadService());
  }

  public static LearningPlanTemplateSummaryResponse toSummaryResponse(
      LearningPlanTemplate template,
      LearningPlanLoadService loadService
  ) {
    return new LearningPlanTemplateSummaryResponse(
        template.templateId(),
        template.title(),
        template.summary(),
        template.catalogCategory(),
        template.recommendedOrder(),
        template.intent(),
        template.defaultDurationWeeks(),
        template.level(),
        template.defaultWeeklyHours(),
        template.programmingLanguage(),
        template.difficultyPreference(),
        template.interviewOriented(),
        template.topicPreferences(),
        template.targetAudience(),
        template.expectedOutcome(),
        template.matchedProblemCount(),
        loadService.defaultLoadSummary(template),
        loadService.defaultRhythmSettings(template));
  }

  public static LearningPlanTemplateDetailResponse toDetailResponse(LearningPlanTemplate template) {
    return toDetailResponse(template, new LearningPlanLoadService());
  }

  public static LearningPlanTemplateDetailResponse toDetailResponse(
      LearningPlanTemplate template,
      LearningPlanLoadService loadService
  ) {
    return new LearningPlanTemplateDetailResponse(
        template.templateId(),
        template.title(),
        template.summary(),
        template.catalogCategory(),
        template.recommendedOrder(),
        template.intent(),
        template.goal(),
        template.defaultDurationWeeks(),
        template.level(),
        template.defaultWeeklyHours(),
        template.programmingLanguage(),
        template.difficultyPreference(),
        template.interviewOriented(),
        template.topicPreferences(),
        template.targetAudience(),
        template.prerequisites(),
        template.recommendedFor(),
        template.notRecommendedFor(),
        template.expectedOutcome(),
        template.sourceName(),
        template.sourceUrl(),
        template.matchedProblemCount(),
        loadService.defaultLoadSummary(template),
        loadService.defaultRhythmSettings(template),
        template.phases().stream().map(LearningPlanTemplateResponseMapper::toPhaseResponse).toList());
  }

  private static LearningPlanTemplatePhaseResponse toPhaseResponse(LearningPlanTemplatePhase phase) {
    return new LearningPlanTemplatePhaseResponse(
        phase.phaseIndex(),
        phase.title(),
        phase.durationWeeks(),
        phase.focus(),
        phase.objectives(),
        phase.recommendedTags(),
        phase.acceptanceCriteria(),
        phase.reviewAdvice(),
        (int) phase.problemRefs().stream().filter(ref -> ref.matchedProblem()).count());
  }

  public static List<LearningPlanTemplateSummaryResponse> toSummaryResponses(List<LearningPlanTemplate> templates) {
    return toSummaryResponses(templates, new LearningPlanLoadService());
  }

  public static List<LearningPlanTemplateSummaryResponse> toSummaryResponses(
      List<LearningPlanTemplate> templates,
      LearningPlanLoadService loadService
  ) {
    return templates.stream()
        .map(template -> LearningPlanTemplateResponseMapper.toSummaryResponse(template, loadService))
        .toList();
  }

  private LearningPlanTemplateResponseMapper() {
  }
}
