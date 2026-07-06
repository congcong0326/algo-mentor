package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplate;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplatePhase;
import org.congcong.algomentor.mentor.application.learningplan.template.LearningPlanTemplateProblemRef;

public final class LearningPlanTemplateResponseMapper {

  public static LearningPlanTemplateSummaryResponse toSummaryResponse(LearningPlanTemplate template) {
    return new LearningPlanTemplateSummaryResponse(
        template.templateId(),
        template.title(),
        template.summary(),
        template.intent(),
        template.defaultDurationWeeks(),
        template.level(),
        template.defaultWeeklyHours(),
        template.difficultyPreference(),
        template.interviewOriented(),
        template.topicPreferences(),
        template.targetAudience(),
        template.difficultyMix(),
        template.expectedOutcome(),
        template.sourceName(),
        template.sourceCommit(),
        template.problemCount(),
        template.matchedProblemCount(),
        template.missingProblemCount());
  }

  public static LearningPlanTemplateDetailResponse toDetailResponse(LearningPlanTemplate template) {
    return new LearningPlanTemplateDetailResponse(
        template.templateId(),
        template.title(),
        template.summary(),
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
        template.difficultyMix(),
        template.prerequisites(),
        template.recommendedFor(),
        template.notRecommendedFor(),
        template.expectedOutcome(),
        template.sourceName(),
        template.sourceUrl(),
        template.sourceCommit(),
        template.sourceDataPath(),
        template.sourceDescription(),
        template.curationNotes(),
        template.licenseNotice(),
        template.problemCount(),
        template.matchedProblemCount(),
        template.missingProblemCount(),
        template.metadata(),
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
        phase.problemRefs().stream().map(LearningPlanTemplateResponseMapper::toProblemRefResponse).toList());
  }

  private static LearningPlanTemplateProblemRefResponse toProblemRefResponse(LearningPlanTemplateProblemRef ref) {
    return new LearningPlanTemplateProblemRefResponse(
        ref.phaseIndex(),
        ref.sortOrder(),
        ref.sourceOrder(),
        ref.problemSlug(),
        ref.sourceTitle(),
        ref.sourceDifficulty(),
        ref.pattern(),
        ref.sourceUrl(),
        ref.matchedProblem(),
        ref.metadata());
  }

  public static List<LearningPlanTemplateSummaryResponse> toSummaryResponses(List<LearningPlanTemplate> templates) {
    return templates.stream()
        .map(LearningPlanTemplateResponseMapper::toSummaryResponse)
        .toList();
  }

  private LearningPlanTemplateResponseMapper() {
  }
}
