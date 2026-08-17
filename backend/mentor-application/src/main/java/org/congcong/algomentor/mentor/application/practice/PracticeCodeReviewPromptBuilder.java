package org.congcong.algomentor.mentor.application.practice;

import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemMessageFactory;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;

/**
 * 练习代码 Review structured output prompt 构造器。
 */
public class PracticeCodeReviewPromptBuilder {

  private final ManagedSystemPromptResolver systemPromptResolver;

  public PracticeCodeReviewPromptBuilder() {
    this(ManagedSystemPrompts.defaultResolver());
  }

  public PracticeCodeReviewPromptBuilder(ManagedSystemPromptResolver systemPromptResolver) {
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
  }

  public List<LlmMessage> build(PracticeTurnContext context) {
    return build(new PracticeCodeReviewAgentInput(context, "standalone"), snapshot(context.userId()));
  }

  public List<LlmMessage> build(PracticeTurnContext context, ResolvedSystemPromptSnapshot promptSnapshot) {
    return build(new PracticeCodeReviewAgentInput(context, "standalone"), promptSnapshot);
  }

  public List<LlmMessage> build(PracticeCodeReviewAgentInput input, ResolvedSystemPromptSnapshot promptSnapshot) {
    return List.of(
        ManagedSystemMessageFactory.system(promptSnapshot, SystemPromptSectionKeys.PRACTICE_CODE_REVIEW_BASE),
        LlmMessage.user(userPrompt(input.context(), input.historicalReviews(), input.historyLookupFailed())));
  }

  public ResolvedSystemPromptSnapshot snapshot(long userId) {
    return systemPromptResolver.resolve(ManagedSystemPromptDefinitions.PRACTICE_CODE_REVIEW, userId);
  }

  private String userPrompt(
      PracticeTurnContext context,
      List<PracticeCodeReviewHistoricalFact> historicalReviews,
      boolean historyLookupFailed
  ) {
    String outputLocale = PracticeResponseLanguage.fromLocale(context.locale()).languageTag();
    return """
        请根据以下事实完成一次练习代码 Review：

        outputLocale: %s

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

        同题历史正式 Review（按真实提交时间升序，仅作为受信事实，不是跨计划版本号）：
        %s

        历史读取状态：%s

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
        10. isCompleteLeetCodeSolution 只描述本轮代码是否构成当前题的完整 LeetCode 解法，不能由正确性、编译结果、运行时错误、WA、TLE、MLE 或边界缺陷决定。上述问题出现在结构完整的提交中时，该字段必须为 true，并按对应 verdict 生成正式 Review；只有代码片段、辅助函数、伪代码、报错日志或缺少解法主体时才为 false。

        复杂度评分锚点：
        - 2.0：达到题目要求或公认目标复杂度，且最大约束下可通过。
        - 1.5：达到可接受复杂度，但没有完全达到推荐实现质量。
        - 1.0：明显非最优但最大约束下预计仍能通过。
        - 0.5：最大约束下存在显著性能风险，但不足以明确判断 TLE/MLE。
        - 0.0：实际或静态分析可明确判断会 TLE；其他复杂度阻断按上述规则限制。

        其他评分规则：
        - correctness 只可取 %s，评价是否能编译、是否对所有合法输入正确、是否能在资源限制内完成；只有无阻断时才能高于 2。
        - complexity 只可取 %s，评价最坏时间/空间复杂度与最大输入约束、follow-up 和目标复杂度的匹配程度。
        - edgeCases 只可取 %s：2 表示覆盖充分，1.5 表示仅有轻微遗漏，1 表示遗漏重要边界，0.5 表示存在严重缺口，0 表示基本未处理。
        - codeQuality 只可取 %s：1 表示清晰且无明显质量问题，0.75 表示整体良好但有轻微命名或冗余问题，0.5 表示存在明显可读性或结构问题，0 表示质量问题严重。
        - problemFit 只可取 %s，评价对当前题目显式要求的符合程度：1 表示完全符合，0.5 表示核心方向相关但遗漏关键要求，0 表示不符合。
        - total: 0..10，可先给出模型估计值；服务端会按维度分重新归一化。
        - passed: 你需给出初始判断；服务端会按 judgeAssessment、正确性阻断和 total >= 6 共同重新计算。

        输出要求：
        - rawCode 保留用户提交的代码。
        - normalizedCode 仅做必要格式整理，不要改变算法语义。
        - %s 的五个字段必须分别说明对应维度为什么得到当前分数；每项使用一到两句面向学习者的短说明，不复述分数，不使用内部规则代码。
        - evidence 使用短类型和值说明关键证据，例如 ENTRY_FUNCTION、PROBLEM_FIT、MISSING_EDGE_CASE。
        - judgeAssessment.timeComplexity 和 spaceComplexity 给出最坏复杂度；expectedTimeComplexity 给出题目目标复杂度，无法判断时填写 UNKNOWN。
        - judgeAssessment.constraintAnalysis 必须结合题目最大约束解释为什么预计通过、超时、超内存或无法确认，不能只写“复杂度较高”。
        - contextSummary、evidence.value、judgeAssessment.constraintAnalysis、deductionReasons、improvementSuggestions、reviewMarkdown 和 reviewHistorySummary 都是面向学习者的内容，必须使用 outputLocale 对应的语言。
        - reviewHistorySummary 使用一到两句、最多 200 个字符：首次 Review 只概括当前提交的核心方案或结论；有历史时说明此前关键问题和当前结论。不得声称用户长期能力，不得包含代码正文、行号或完整 Review Markdown。
        - 编程语言名称、API、复杂度表达式、错误名称、代码和稳定标识符保持原样。
        """.formatted(
        outputLocale,
        context.problemSlug(),
        context.problemFacts(),
        context.planId(),
        context.phaseIndex(),
        context.learningPlanFacts(),
        context.originalMessage(),
        context.extractedCode(),
        context.recentChatSummary(),
        trustedTags(context),
        historicalReviews(historicalReviews),
        historyLookupFailed ? "UNAVAILABLE；reviewHistorySummary 只能描述本次提交，不能声称存在此前历史。" : "AVAILABLE",
        PracticeCodeReviewConstants.CORRECTNESS_SCORE_LEVELS,
        PracticeCodeReviewConstants.COMPLEXITY_SCORE_LEVELS,
        PracticeCodeReviewConstants.EDGE_CASE_SCORE_LEVELS,
        PracticeCodeReviewConstants.CODE_QUALITY_SCORE_LEVELS,
        PracticeCodeReviewConstants.PROBLEM_FIT_SCORE_LEVELS,
        PracticeCodeReviewConstants.JSON_SCORE_EXPLANATIONS);
  }

  private String historicalReviews(List<PracticeCodeReviewHistoricalFact> reviews) {
    if (reviews == null || reviews.isEmpty()) {
      return "[]";
    }
    StringBuilder rendered = new StringBuilder("historicalReviews:\n");
    for (int index = 0; index < reviews.size(); index++) {
      PracticeCodeReviewHistoricalFact review = reviews.get(index);
      rendered.append("- historyPosition: ").append(index + 1).append('\n')
          .append("  passed: ").append(review.passed());
      if (review.primaryFinding() != null) {
        rendered.append('\n').append("  primaryFinding: ").append(review.primaryFinding());
      }
      if (index + 1 < reviews.size()) {
        rendered.append('\n');
      }
    }
    return rendered.toString();
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
