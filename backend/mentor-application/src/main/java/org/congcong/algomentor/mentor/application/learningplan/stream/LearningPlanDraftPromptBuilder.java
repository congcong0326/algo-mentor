package org.congcong.algomentor.mentor.application.learningplan.stream;

import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;

/**
 * 学习计划草案生成 prompt 构造器。
 */
public class LearningPlanDraftPromptBuilder {

  public List<LlmMessage> build(LearningPlanDraftCommand command) {
    return List.of(
        LlmMessage.system(systemPrompt()),
        LlmMessage.user(userPrompt(command)));
  }

  private String systemPrompt() {
    return """
        你是 algo-mentor 的算法学习计划规划 Agent。你必须输出符合 JSON Schema 的学习计划草案。

        规则：
        1. 先使用 list_problem_filters 了解本地题库标签和难度，再用 search_problems 搜索候选题。
        2. 推荐题必须来自 search_problems 返回的本地题库候选；不要编造 slug、标题、难度或标签。
        3. 如果候选不足，可以少推荐题，并在 metadata.problemRecommendationIncomplete 标记 true。
        4. 计划阶段、目标、验收标准和复盘建议使用中文。
        5. 阶段数按周期规划：1 周 1 阶段，2 周 2 阶段，3-6 周 3 阶段，7 周及以上 4 阶段。
        6. 各阶段 durationWeeks 之和必须等于总周期；每阶段最多 5 道题。
        7. 最终只输出结构化 JSON，不要输出 Markdown 或解释文本。
        """;
  }

  private String userPrompt(LearningPlanDraftCommand command) {
    return """
        请为以下用户生成学习计划草案：

        intent: %s
        goal: %s
        durationWeeks: %s
        level: %s
        weeklyHours: %s
        programmingLanguage: %s
        difficultyPreference: %s
        interviewOriented: %s
        topicPreferences: %s
        """.formatted(
        command.intent(),
        command.goal(),
        command.durationWeeks(),
        command.level(),
        command.weeklyHours(),
        command.programmingLanguage(),
        command.difficultyPreference(),
        command.interviewOriented(),
        command.topicPreferences());
  }
}
