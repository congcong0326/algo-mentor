package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewPromptBuilderTest {

  @Test
  void requiresJudgeFirstHardGatesAndSuboptimalScoreCap() {
    List<LlmMessage> messages = new PracticeCodeReviewPromptBuilder().build(context("zh-CN"));

    assertThat(messages).hasSize(2);
    assertThat(messages.get(1).text())
        .contains("先判断 judgeAssessment，再进行分项评分")
        .contains("TLE 时 complexity=0")
        .contains("total<=8；不得给 9 分或 10 分")
        .contains("basis=USER_REPORTED_EXECUTION")
        .contains("n 最大为 100000")
        .contains("expectedTimeComplexity")
        .contains("codeQuality 只可取 [0, 0.5, 0.75, 1]")
        .contains("problemFit 只可取 [0, 0.5, 1]")
        .contains("题目显式要求的符合程度")
        .contains("scoreExplanations 的五个字段必须分别说明对应维度为什么得到当前分数");
  }

  @Test
  void injectsEnglishOutputLocaleForUserVisibleReviewFields() {
    List<LlmMessage> messages = new PracticeCodeReviewPromptBuilder().build(context("en-US"));

    assertThat(messages.get(1).text())
        .contains("outputLocale: en-US")
        .contains("必须使用 outputLocale 对应的语言")
        .doesNotContain("deductionReasons 和 improvementSuggestions 使用中文短句")
        .doesNotContain("reviewMarkdown 使用中文");
  }

  @Test
  void injectsOnlyBoundedHistoricalFactsWithoutCodeOrReviewMarkdown() {
    List<LlmMessage> messages = new PracticeCodeReviewPromptBuilder().build(
        new PracticeCodeReviewAgentInput(
            context("zh-CN"),
            "review-key",
            List.of(
                new PracticeCodeReviewHistoricalFact(
                    11L, false, "遗漏空前缀初始化", java.time.Instant.parse("2026-01-01T00:00:00Z")),
                new PracticeCodeReviewHistoricalFact(
                    12L, true, null, java.time.Instant.parse("2026-01-02T00:00:00Z")))),
        new PracticeCodeReviewPromptBuilder().snapshot(7L));

    assertThat(messages.get(1).text())
        .contains("historicalReviews:")
        .contains("historyPosition: 1")
        .contains("passed: false")
        .contains("primaryFinding: 遗漏空前缀初始化")
        .contains("历史读取状态：AVAILABLE")
        .doesNotContain("reviewId:")
        .doesNotContain("versionNo:");
  }

  @Test
  void marksUnavailableHistorySoTheModelCannotClaimPriorFacts() {
    List<LlmMessage> messages = new PracticeCodeReviewPromptBuilder().build(
        new PracticeCodeReviewAgentInput(context("zh-CN"), "review-key", List.of(), true),
        new PracticeCodeReviewPromptBuilder().snapshot(7L));

    assertThat(messages.get(1).text())
        .contains("历史读取状态：UNAVAILABLE")
        .contains("reviewHistorySummary 只能描述本次提交")
        .doesNotContain("historicalReviews:\n-");
  }

  private PracticeTurnContext context(String locale) {
    return new PracticeTurnContext(
        7L,
        12L,
        1,
        "two-sum",
        50L,
        701L,
        702L,
        501L,
        "题目要求返回两数下标，n 最大为 100000。",
        "哈希表阶段",
        "class Solution {}",
        "请 Review",
        "",
        locale);
  }
}
