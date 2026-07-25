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
        6. affectedTagIds 只能从服务端提供的受信标签候选中选择；不确定或无关时返回空数组。
        7. 不得把“思路基本正确”直接等同于“能够通过在线评测”；必须检查编译、反例、最大约束下的时间复杂度和空间复杂度。
        8. 用户明确提供的 AC、WA、TLE、MLE、Compile Error 或 Runtime Error 只能标记为 USER_REPORTED_EXECUTION；只有服务端事实中明确提供的执行结果才能标记为 SERVER_EXECUTION。
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

        当前题目受信标签候选（只可从这些 tagId 选择 affectedTagIds）：
        %s

        判定顺序与硬门槛：
        1. 先判断 judgeAssessment，再进行分项评分。不要先算总分再反推是否能通过评测。
        2. verdict 只能是 ACCEPTED、LIKELY_ACCEPTED、WRONG_ANSWER、TIME_LIMIT_EXCEEDED、MEMORY_LIMIT_EXCEEDED、COMPILE_ERROR、RUNTIME_ERROR、UNKNOWN。
        3. basis 只能是 SERVER_EXECUTION、USER_REPORTED_EXECUTION、STATIC_ANALYSIS、INSUFFICIENT_CONTEXT。
        4. 只有 ACCEPTED 或 LIKELY_ACCEPTED 可以通过；其余 verdict 都属于评测阻断，blockingIssue 必须为 true。
        5. 编译失败、存在合法反例、实际或预计 WA/TLE/MLE/RE、缺少核心流程、无法确认最大数据范围可通过，都属于评测阻断。
        6. 评测阻断时 correctness <= 2、total <= 5、passed=false；TLE 时 complexity=0，MLE 时 complexity<=0.5。代码质量、边界处理或题意贴合不得抵消阻断问题。
        7. 如果代码预计可以通过，但没有达到题目明确要求、follow-up 或公认目标复杂度，meetsExpectedComplexity=false、complexity<=1、total<=8；不得给 9 分或 10 分。
        8. 如果缺少题面约束且无法可靠判断性能，verdict=UNKNOWN、basis=INSUFFICIENT_CONTEXT、blockingIssue=true，不得声称正式通过。
        9. 如果 originalMessage 明确报告本次提交为 WA、TLE、MLE、Compile Error 或 Runtime Error，且没有相反的服务端执行事实，必须采用对应 verdict、basis=USER_REPORTED_EXECUTION、blockingIssue=true。

        复杂度评分锚点：
        - 2.0：达到题目要求或公认目标复杂度，且最大约束下可通过。
        - 1.5：达到可接受复杂度，但没有完全达到推荐实现质量。
        - 1.0：明显非最优但最大约束下预计仍能通过。
        - 0.5：最大约束下存在显著性能风险，但不足以明确判断 TLE/MLE。
        - 0.0：实际或静态分析可明确判断会 TLE；其他复杂度阻断按上述规则限制。

        其他评分规则：
        - correctness: 0..4，是否能编译、是否对所有合法输入正确、是否能在资源限制内完成；只有无阻断时才能高于 2。
        - complexity: 0..2，最坏时间/空间复杂度与最大输入约束、follow-up 和目标复杂度的匹配程度。
        - edgeCases: 0..2，边界条件覆盖情况。
        - codeQuality: 0..1，可读性、命名、冗余和 LeetCode 提交格式。
        - problemFit: 0..1，是否解决当前题目而不是其他题目。
        - total: 0..10，可先给出模型估计值；服务端会按维度分重新归一化。
        - passed: 你需给出初始判断；服务端会按 judgeAssessment、正确性阻断和 total >= 6 共同重新计算。

        输出要求：
        - rawCode 保留用户提交的代码。
        - normalizedCode 仅做必要格式整理，不要改变算法语义。
        - evidence 使用短类型和值说明关键证据，例如 ENTRY_FUNCTION、PROBLEM_FIT、MISSING_EDGE_CASE。
        - judgeAssessment.timeComplexity 和 spaceComplexity 给出最坏复杂度；expectedTimeComplexity 给出题目目标复杂度，无法判断时填写 UNKNOWN。
        - judgeAssessment.constraintAnalysis 必须结合题目最大约束解释为什么预计通过、超时、超内存或无法确认，不能只写“复杂度较高”。
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
        context.recentChatSummary(),
        trustedTags(context));
  }

  private String trustedTags(PracticeTurnContext context) {
    if (context.trustedProblemTags().isEmpty()) {
      return "[]";
    }
    return context.trustedProblemTags().stream()
        .map(tag -> "{tagId=%d,value=%s,labelEn=%s,labelZh=%s}".formatted(
            tag.tagId(), tag.value(), tag.labelEn(), tag.labelZh()))
        .collect(java.util.stream.Collectors.joining("\n"));
  }
}
