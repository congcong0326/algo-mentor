package org.congcong.algomentor.mentor.application.learningplan.proposal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;

/**
 * 学习计划提案 prompt 构造器。
 */
public class LearningPlanProposalPromptBuilder {

  private final ObjectMapper objectMapper;

  public LearningPlanProposalPromptBuilder(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  public List<LlmMessage> buildDraftRevisionPrompt(
      String instruction,
      LearningPlanDraftCommand command,
      LearningPlanDraftPlan currentPlan
  ) {
    return List.of(
        LlmMessage.system("你是 algo-mentor 的学习计划修订 Agent。最终只输出完整学习计划草案 JSON。"),
        LlmMessage.user("""
            用户修订要求：
            %s

            原始生成命令 JSON：
            %s

            当前学习计划草案 JSON：
            %s
            """.formatted(instruction, toJson(command), toJson(currentPlan))));
  }

  public List<LlmMessage> buildExtensionPrompt(
      String instruction,
      LearningPlan currentPlan,
      List<PracticeProgress> progress
  ) {
    return List.of(
        LlmMessage.system(extensionSystemPrompt()),
        LlmMessage.user("""
            请基于当前学习计划和练习进度生成学习计划扩展草案。

            用户扩展要求：
            %s

            当前学习计划 JSON：
            %s

            练习进度 JSON：
            %s
            """.formatted(instruction, toJson(currentPlan.plan()), toJson(progressSummary(progress)))));
  }

  public List<LlmMessage> buildExtensionRevisionPrompt(
      String instruction,
      LearningPlan currentPlan,
      List<PracticeProgress> progress,
      LearningPlanExtensionDraft previousExtension
  ) {
    return List.of(
        LlmMessage.system(extensionSystemPrompt()),
        LlmMessage.assistant("""
            上一版扩展草案 JSON：
            %s
            """.formatted(toJson(previousExtension))),
        LlmMessage.user("""
            请基于当前学习计划、练习进度和上一版扩展草案，生成新的学习计划扩展草案。

            用户修订要求：
            %s

            当前学习计划 JSON：
            %s

            练习进度 JSON：
            %s
            """.formatted(instruction, toJson(currentPlan.plan()), toJson(progressSummary(progress)))));
  }

  private String extensionSystemPrompt() {
    return """
        你是 algo-mentor 的学习计划扩展 Agent。你必须输出符合 JSON Schema 的扩展草案。

        规则：
        1. 先使用 list_problem_filters 了解本地题库标签和难度，再用 search_problems 搜索候选题。
        2. 只能追加新阶段，不能删除、修改、重排已有阶段。
        3. 新增题目不能和已有计划题目重复。
        4. 新增题目必须来自本地题库工具。
        5. 每个新增阶段最多 5 道题；候选不足时可以少推荐，并在 metadata.problemRecommendationIncomplete 标记 true。
        6. 阶段、目标、验收标准、复盘建议和 summary 使用中文。
        7. 最终只输出扩展草案 JSON，不输出完整替换版计划。
        """;
  }

  private List<Map<String, Object>> progressSummary(List<PracticeProgress> progress) {
    if (progress == null) {
      return List.of();
    }
    return progress.stream()
        .map(item -> {
          Map<String, Object> summary = new LinkedHashMap<>();
          summary.put("phaseIndex", item.phaseIndex());
          summary.put("problemSlug", item.problemSlug());
          summary.put("status", item.status().name());
          return summary;
        })
        .toList();
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_PROMPT_BUILD_FAILED", "学习计划提案上下文无法序列化。");
    }
  }
}
