package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionDraft;

/**
 * 把模型结构化输出映射为学习计划扩展草案。
 */
public class LearningPlanExtensionStructuredOutputMapper {

  private final ObjectMapper objectMapper;

  public LearningPlanExtensionStructuredOutputMapper(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public LearningPlanExtensionDraft map(JsonNode node) {
    if (node == null || node.isNull()) {
      throw new LearningPlanException("LEARNING_PLAN_EXTENSION_STRUCTURED_OUTPUT_INVALID", "模型未返回扩展提案。");
    }
    try {
      return objectMapper.treeToValue(node, LearningPlanExtensionDraft.class);
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_EXTENSION_STRUCTURED_OUTPUT_INVALID", "扩展提案结构化结果解析失败。");
    }
  }
}
