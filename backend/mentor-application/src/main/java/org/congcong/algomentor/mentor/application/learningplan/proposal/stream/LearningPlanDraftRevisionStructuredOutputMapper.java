package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStructuredOutputMapper;

/** 解析修订模型的 Brief 与生成内容，并恢复服务端持有的运行控制字段。 */
public final class LearningPlanDraftRevisionStructuredOutputMapper {

  private final ObjectMapper objectMapper;
  private final LearningPlanDraftStructuredOutputMapper generatedContentMapper;
  private final LearningPlanDraftValidator validator;

  public LearningPlanDraftRevisionStructuredOutputMapper(
      ObjectMapper objectMapper,
      LearningPlanDraftStructuredOutputMapper generatedContentMapper,
      LearningPlanDraftValidator validator
  ) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.generatedContentMapper = Objects.requireNonNull(generatedContentMapper, "generatedContentMapper");
    this.validator = Objects.requireNonNull(validator, "validator");
  }

  public LearningPlanDraftRevisionOutput map(JsonNode structured, LearningPlanBrief currentBrief) {
    if (structured == null || structured.isNull()
        || !structured.path("resolvedBrief").isObject()
        || !structured.path("generatedContent").isObject()) {
      throw new LearningPlanException("LEARNING_PLAN_STRUCTURED_OUTPUT_INVALID", "学习计划修订结构化结果解析失败。");
    }
    LearningPlanBrief resolvedBrief = resolvedBrief(structured.path("resolvedBrief"), currentBrief);
    return new LearningPlanDraftRevisionOutput(
        resolvedBrief,
        generatedContentMapper.map(structured.path("generatedContent"), resolvedBrief));
  }

  private LearningPlanBrief resolvedBrief(JsonNode node, LearningPlanBrief currentBrief) {
    try {
      LearningPlanBrief candidate = objectMapper.treeToValue(node, LearningPlanBrief.class);
      LearningPlanBrief resolved = new LearningPlanBrief(
          candidate.intent(),
          candidate.objective(),
          candidate.durationWeeks(),
          candidate.level(),
          candidate.weeklyHours(),
          candidate.programmingLanguage(),
          candidate.difficultyDistribution(),
          candidate.topicPreferences(),
          candidate.additionalConstraints(),
          currentBrief.personalizationEnabled(),
          currentBrief.contentLocale());
      List<String> missingFields = validator.missingRequiredFields(resolved);
      if (!missingFields.isEmpty()) {
        throw new LearningPlanException("LEARNING_PLAN_BRIEF_INVALID", "学习计划修订输入不完整或无效。");
      }
      return resolved;
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_STRUCTURED_OUTPUT_INVALID", "学习计划修订结构化结果解析失败。");
    }
  }
}
