package org.congcong.algomentor.mentor.application.practice;

import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;

/**
 * 练习代码 Review structured output prompt 构造器。
 */
public class PracticeCodeReviewPromptBuilder {

  public List<LlmMessage> build(PracticeTurnContext context) {
    return List.of(
        LlmMessage.system(systemPrompt()),
        LlmMessage.user(userPrompt(context)));
  }

  private String systemPrompt() {
    return """
        你是 algo-mentor 的算法代码 Review 助手。你必须判断用户当前轮次是否提交了当前题目的完整 LeetCode 风格解法，并只输出符合 JSON Schema 的结构化结果。

        安全与隐私规则：
        1. 不要在输出中复述、暴露或推断 API key、访问令牌、Authorization 头、数据库密码或其他密钥。
        2. 如果用户消息里包含疑似密钥，只评价算法代码本身，并在 reviewMarkdown 中用概括性中文提醒移除敏感信息。
        3. 不要编造题目事实；如果代码不属于当前题目，belongsToCurrentProblem 必须为 false。
        4. 如果不是代码提交、不是当前题目、或不是完整可 Review 的 LeetCode 解法，对应布尔字段必须为 false。
        5. 最终只输出结构化 JSON，不要输出 Markdown 包裹、解释文本或额外字段。
        """;
  }

  private String userPrompt(PracticeTurnContext context) {
    return """
        请根据以下事实完成一次练习代码 Review：

        当前题目：
        problemSlug: %s
        problemFacts: %s

        学习计划上下文：
        planId: %s
        phaseIndex: %s
        learningPlanFacts: %s

        本轮消息：
        originalMessage: %s

        提取到的代码：
        ```text
        %s
        ```

        最近对话摘要：
        %s

        评分规则：
        - correctness: 0..4，算法正确性与是否能通过核心用例。
        - complexity: 0..2，时间/空间复杂度是否符合题目要求。
        - edgeCases: 0..2，边界条件覆盖情况。
        - codeQuality: 0..1，可读性、命名、冗余和 LeetCode 提交格式。
        - problemFit: 0..1，是否解决当前题目而不是其他题目。
        - total: 0..10，可先给出模型估计值；服务端会按维度分重新归一化。
        - passed: 服务端会按 total >= 6 重新计算，你仍需给出初始判断。

        输出要求：
        - rawCode 保留用户提交的代码。
        - normalizedCode 仅做必要格式整理，不要改变算法语义。
        - evidence 使用短类型和值说明关键证据，例如 ENTRY_FUNCTION、PROBLEM_FIT、MISSING_EDGE_CASE。
        - deductionReasons 和 improvementSuggestions 使用中文短句。
        - reviewMarkdown 使用中文，面向学习者解释主要扣分点和下一步改进。
        """.formatted(
        context.problemSlug(),
        context.problemFacts(),
        context.planId(),
        context.phaseIndex(),
        context.learningPlanFacts(),
        context.originalMessage(),
        context.extractedCode(),
        context.recentChatSummary());
  }
}
