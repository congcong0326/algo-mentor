package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import com.fasterxml.jackson.databind.JsonNode;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionToolContracts;

/** 解析修订 Agent 的有界终态 artifact 引用。 */
public final class LearningPlanDraftRevisionStructuredOutputMapper {

  public LearningPlanDraftRevisionOutput map(JsonNode structured) {
    if (structured == null || !structured.isObject()) {
      throw new LearningPlanException("LEARNING_PLAN_STRUCTURED_OUTPUT_INVALID", "学习计划修订结构化结果解析失败。");
    }
    String status = structured.path("status").asText(null);
    String artifactRef = structured.path("artifactRef").asText(null);
    if (!LearningPlanRevisionToolContracts.FINAL_STATUS_COMPILED.equals(status)
        || artifactRef == null || artifactRef.isBlank()) {
      throw new LearningPlanException("LEARNING_PLAN_STRUCTURED_OUTPUT_INVALID", "学习计划修订结构化结果解析失败。");
    }
    return new LearningPlanDraftRevisionOutput(status, artifactRef.trim());
  }
}
