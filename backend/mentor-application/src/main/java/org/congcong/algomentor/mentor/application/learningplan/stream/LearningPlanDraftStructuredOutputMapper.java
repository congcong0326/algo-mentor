package org.congcong.algomentor.mentor.application.learningplan.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
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

  public LearningPlanDraftPlan map(JsonNode structured, LearningPlanDraftCommand command) {
    if (structured == null || structured.isNull()) {
      throw new LearningPlanException("LEARNING_PLAN_STRUCTURED_OUTPUT_MISSING", "模型未返回学习计划结构化结果。");
    }
    try {
      LearningPlanDraftPlan rawPlan = objectMapper.treeToValue(structured, LearningPlanDraftPlan.class);
      return normalize(rawPlan, command);
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_STRUCTURED_OUTPUT_INVALID", "学习计划结构化结果解析失败。");
    }
  }

  private LearningPlanDraftPlan normalize(LearningPlanDraftPlan rawPlan, LearningPlanDraftCommand command) {
    List<LearningPlanPhaseDraft> phases = new ArrayList<>();
    boolean incomplete = false;
    for (LearningPlanPhaseDraft phase : rawPlan.phases()) {
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
          phase.recommendedTags(),
          phase.acceptanceCriteria(),
          phase.reviewAdvice(),
          problems));
    }
    Map<String, Object> metadata = new LinkedHashMap<>(rawPlan.metadata());
    if (incomplete) {
      metadata.put("problemRecommendationIncomplete", true);
    }
    return new LearningPlanDraftPlan(
        rawPlan.title(),
        rawPlan.summary(),
        command.intent(),
        command.goal(),
        command.durationWeeks(),
        command.level(),
        command.weeklyHours(),
        command.programmingLanguage(),
        command.difficultyPreference(),
        command.interviewOriented(),
        command.topicPreferences(),
        rawPlan.profileSummary(),
        phases,
        metadata);
  }
}
