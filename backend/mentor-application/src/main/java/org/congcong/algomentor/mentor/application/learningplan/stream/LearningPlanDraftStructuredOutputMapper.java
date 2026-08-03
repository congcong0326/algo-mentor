package org.congcong.algomentor.mentor.application.learningplan.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;

/**
 * 把模型结构化输出映射为领域草案，并用本地题库事实校验推荐题。
 */
public class LearningPlanDraftStructuredOutputMapper {

  private final ObjectMapper objectMapper;
  private final LearningPlanProblemCatalog problemCatalog;

  public LearningPlanDraftStructuredOutputMapper(ObjectMapper objectMapper, LearningPlanProblemCatalog problemCatalog) {
    this.objectMapper = objectMapper;
    this.problemCatalog = problemCatalog;
  }

  public LearningPlanDraftPlan map(JsonNode structured, LearningPlanBrief brief) {
    if (structured == null || structured.isNull()) {
      throw new LearningPlanException("LEARNING_PLAN_STRUCTURED_OUTPUT_MISSING", "模型未返回学习计划结构化结果。");
    }
    try {
      LearningPlanGeneratedContent generatedContent = objectMapper.treeToValue(
          structured,
          LearningPlanGeneratedContent.class);
      return normalize(generatedContent.title(), generatedContent.summary(), generatedContent.phases(),
          generatedContent.metadata(), brief);
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_STRUCTURED_OUTPUT_INVALID", "学习计划结构化结果解析失败。");
    }
  }

  private LearningPlanDraftPlan normalize(
      String title,
      String summary,
      List<LearningPlanPhaseDraft> rawPhases,
      Map<String, Object> rawMetadata,
      LearningPlanBrief brief
  ) {
    List<LearningPlanPhaseDraft> phases = new ArrayList<>();
    boolean incomplete = false;
    for (LearningPlanPhaseDraft phase : rawPhases == null ? List.<LearningPlanPhaseDraft>of() : rawPhases) {
      List<LearningPlanProblemDraft> problems = new ArrayList<>();
      int sortOrder = 1;
      for (LearningPlanProblemDraft problem : phase.problems()) {
        if (problem.slug() == null || problem.slug().isBlank()) {
          continue;
        }
        LearningPlanProblemCandidate candidate = problemCatalog.findBySlug(problem.slug()).orElse(null);
        if (candidate == null) {
          incomplete = true;
          continue;
        }
        problems.add(LearningPlanProblemDraft.fromCandidate(candidate, sortOrder++, problem.reason()));
        if (problems.size() >= 5) {
          break;
        }
      }
      incomplete = incomplete || problems.size() < Math.min(3, phase.problems().size());
      phases.add(new LearningPlanPhaseDraft(
          phase.phaseIndex(),
          phase.title(),
          phase.durationWeeks(),
          phase.focus(),
          phase.objectives(),
          canonicalTags(phase.recommendedTags(), brief.contentLocale().languageTag()),
          phase.acceptanceCriteria(),
          phase.reviewAdvice(),
          problems));
    }
    Map<String, Object> metadata = new LinkedHashMap<>(rawMetadata == null ? Map.of() : rawMetadata);
    metadata.put(LearningPlanDraftMetadataKeys.CONTENT_LOCALE, brief.contentLocale().languageTag());
    metadata.put(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED, brief.personalizationEnabled());
    if (incomplete) {
      metadata.put(LearningPlanDraftMetadataKeys.PROBLEM_RECOMMENDATION_INCOMPLETE, true);
    }
    return new LearningPlanDraftPlan(
        title,
        summary,
        brief.intent(),
        brief.objective(),
        brief.durationWeeks(),
        brief.level(),
        brief.weeklyHours(),
        brief.programmingLanguage(),
        brief.difficultyDistribution(),
        brief.interviewOriented(),
        brief.topicPreferences(),
        brief.additionalConstraints(),
        phases,
        metadata);
  }

  private List<String> canonicalTags(List<String> tags, String locale) {
    if (tags == null) {
      return List.of();
    }
    return tags.stream()
        .flatMap(tag -> problemCatalog.findCanonicalTagValue(tag, locale).stream())
        .distinct()
        .toList();
  }

}
