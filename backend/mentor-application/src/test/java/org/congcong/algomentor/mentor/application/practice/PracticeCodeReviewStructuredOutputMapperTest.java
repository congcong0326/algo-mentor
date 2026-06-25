package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewStructuredOutputMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final PracticeCodeReviewStructuredOutputMapper mapper = new PracticeCodeReviewStructuredOutputMapper();

  @Test
  void normalizesTotalAndPassedFromScores() {
    PracticeReviewResult result = mapper.map(context(), structuredOutput("""
        {
          "isCodeSubmission": true,
          "belongsToCurrentProblem": true,
          "isCompleteLeetCodeSolution": true,
          "language": "java",
          "rawCode": "class Solution { int climbStairs(int n) { return n; } }",
          "normalizedCode": "class Solution { public int climbStairs(int n) { return n; } }",
          "evidence": [{"type": "ENTRY_FUNCTION", "value": "climbStairs"}],
          "contextSummary": "用户提交了 Java 解法。",
          "scores": {
            "correctness": 3.0,
            "complexity": 2.0,
            "edgeCases": 1.0,
            "codeQuality": 1.0,
            "problemFit": 1.0,
            "total": 8.8
          },
          "passed": false,
          "deductionReasons": ["边界覆盖不足"],
          "improvementSuggestions": ["补充 n=1 的处理"],
          "reviewMarkdown": "整体可改进。"
        }
        """));

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.REVIEWED);
    assertThat(result.failureCode()).isNull();
    assertThat(result.draft()).isPresent();
    PracticeCodeReviewDraft draft = result.draft().orElseThrow();
    assertThat(draft.score().total()).isEqualByComparingTo(new BigDecimal("8.0"));
    assertThat(draft.passed()).isTrue();
    assertThat(draft.rawCode()).isEqualTo("class Solution { int climbStairs(int n) { return n; } }");
    assertThat(draft.normalizedCode())
        .isEqualTo("class Solution { public int climbStairs(int n) { return n; } }");
    assertThat(draft.userMessageId()).isEqualTo(701L);
    assertThat(draft.agentRunDbId()).isEqualTo(501L);
  }

  @Test
  void rejectsCompleteSubmissionWithEmptyCode() {
    PracticeReviewResult result = mapper.map(context(), structuredOutput("""
        {
          "isCodeSubmission": true,
          "belongsToCurrentProblem": true,
          "isCompleteLeetCodeSolution": true,
          "language": "java",
          "rawCode": " ",
          "normalizedCode": "class Solution {}",
          "evidence": [],
          "contextSummary": "模型认为是完整提交。",
          "scores": {
            "correctness": 3.0,
            "complexity": 2.0,
            "edgeCases": 1.0,
            "codeQuality": 1.0,
            "problemFit": 1.0,
            "total": 8.0
          },
          "passed": true,
          "deductionReasons": [],
          "improvementSuggestions": [],
          "reviewMarkdown": "通过。"
        }
        """));

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(PracticeReviewResult.INVALID_STRUCTURED_OUTPUT);
    assertThat(result.draft()).isEmpty();
  }

  @Test
  void returnsNotCompleteWhenModelRejectsCurrentProblem() {
    PracticeReviewResult result = mapper.map(context(), structuredOutput("""
        {
          "isCodeSubmission": true,
          "belongsToCurrentProblem": false,
          "isCompleteLeetCodeSolution": true,
          "language": "java",
          "rawCode": "class Solution {}",
          "normalizedCode": "class Solution {}",
          "evidence": [{"type": "PROBLEM_MISMATCH", "value": "提交内容与当前题目不匹配"}],
          "contextSummary": "用户可能提交了其他题目的代码。",
          "scores": {
            "correctness": 0.0,
            "complexity": 0.0,
            "edgeCases": 0.0,
            "codeQuality": 0.0,
            "problemFit": 0.0,
            "total": 0.0
          },
          "passed": false,
          "deductionReasons": ["不是当前题目"],
          "improvementSuggestions": ["粘贴当前题目的完整代码"],
          "reviewMarkdown": "这不是当前题目的提交。"
        }
        """));

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.NOT_COMPLETE_SUBMISSION);
    assertThat(result.failureCode()).isNull();
    assertThat(result.draft()).isEmpty();
  }

  @Test
  void capsTotalAtFiveWhenCorrectnessIsBlocking() {
    PracticeReviewResult result = mapper.map(context(), structuredOutput("""
        {
          "isCodeSubmission": true,
          "belongsToCurrentProblem": true,
          "isCompleteLeetCodeSolution": true,
          "language": "java",
          "rawCode": "class Solution { int climbStairs(int n) { return n; } }",
          "normalizedCode": "class Solution { public int climbStairs(int n) { return n; } }",
          "evidence": [{"type": "ENTRY_FUNCTION", "value": "climbStairs"}],
          "contextSummary": "代码结构完整但核心逻辑错误。",
          "scores": {
            "correctness": 2.0,
            "complexity": 2.0,
            "edgeCases": 2.0,
            "codeQuality": 1.0,
            "problemFit": 1.0,
            "total": 8.0
          },
          "passed": true,
          "deductionReasons": ["递推关系错误"],
          "improvementSuggestions": ["使用 f(n)=f(n-1)+f(n-2)"],
          "reviewMarkdown": "核心正确性不足。"
        }
        """));

    PracticeCodeReviewDraft draft = result.draft().orElseThrow();
    assertThat(result.status()).isEqualTo(PracticeReviewStatus.REVIEWED);
    assertThat(draft.score().total()).isEqualByComparingTo(new BigDecimal("5.0"));
    assertThat(draft.passed()).isFalse();
    assertThat(draft.evidence())
        .extracting(PracticeCodeReviewEvidence::type)
        .contains(PracticeCodeReviewConstants.EVIDENCE_CORRECTNESS_BLOCKING_CAP);
  }

  @Test
  void rejectsScoreAboveDimensionLimit() {
    PracticeReviewResult result = mapper.map(context(), structuredOutput("""
        {
          "isCodeSubmission": true,
          "belongsToCurrentProblem": true,
          "isCompleteLeetCodeSolution": true,
          "language": "java",
          "rawCode": "class Solution { int climbStairs(int n) { return n; } }",
          "normalizedCode": "class Solution { public int climbStairs(int n) { return n; } }",
          "evidence": [{"type": "ENTRY_FUNCTION", "value": "climbStairs"}],
          "contextSummary": "分数越界。",
          "scores": {
            "correctness": 4.1,
            "complexity": 2.0,
            "edgeCases": 1.0,
            "codeQuality": 1.0,
            "problemFit": 1.0,
            "total": 9.1
          },
          "passed": true,
          "deductionReasons": [],
          "improvementSuggestions": [],
          "reviewMarkdown": "无效分数。"
        }
        """));

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(PracticeReviewResult.INVALID_STRUCTURED_OUTPUT);
    assertThat(result.draft()).isEmpty();
  }

  @Test
  void rejectsMalformedDecisionFlags() {
    PracticeReviewResult result = mapper.map(context(), structuredOutput("""
        {
          "isCodeSubmission": "true",
          "belongsToCurrentProblem": true,
          "isCompleteLeetCodeSolution": true,
          "language": "java",
          "rawCode": "class Solution { int climbStairs(int n) { return n; } }",
          "normalizedCode": "class Solution { public int climbStairs(int n) { return n; } }",
          "evidence": [],
          "contextSummary": "类型错误。",
          "scores": {
            "correctness": 3.0,
            "complexity": 2.0,
            "edgeCases": 1.0,
            "codeQuality": 1.0,
            "problemFit": 1.0,
            "total": 8.0
          },
          "passed": true,
          "deductionReasons": [],
          "improvementSuggestions": [],
          "reviewMarkdown": "无效判定字段。"
        }
        """));

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(PracticeReviewResult.INVALID_STRUCTURED_OUTPUT);
    assertThat(result.draft()).isEmpty();
  }

  private PracticeTurnContext context() {
    return new PracticeTurnContext(
        7L,
        12L,
        1,
        "climbing-stairs",
        50L,
        701L,
        702L,
        501L,
        "Climbing Stairs",
        "动态规划入门阶段",
        "class Solution { int climbStairs(int n) { return n; } }",
        "请 review 我的代码",
        "最近在讨论递推定义。",
        "zh-CN");
  }

  private JsonNode structuredOutput(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(exception);
    }
  }
}
