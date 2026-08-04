package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
          "evidence": [
            {"type": "ENTRY_FUNCTION", "value": "climbStairs"},
            {"type": "SCORE_CORRECTNESS", "value": "不应保留的旧说明"}
          ],
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
          "reviewMarkdown": "整体可改进。",
          "affectedTagIds": []
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
    assertThat(draft.evidence())
        .extracting(PracticeCodeReviewEvidence::type)
        .contains(
            PracticeCodeReviewConstants.EVIDENCE_SCORE_CORRECTNESS,
            PracticeCodeReviewConstants.EVIDENCE_SCORE_COMPLEXITY,
            PracticeCodeReviewConstants.EVIDENCE_SCORE_EDGE_CASES,
            PracticeCodeReviewConstants.EVIDENCE_SCORE_CODE_QUALITY,
            PracticeCodeReviewConstants.EVIDENCE_SCORE_PROBLEM_FIT);
    assertThat(draft.evidence())
        .extracting(PracticeCodeReviewEvidence::value)
        .doesNotContain("不应保留的旧说明");
    assertThat(draft.userMessageId()).isEqualTo(701L);
    assertThat(draft.agentRunDbId()).isEqualTo(501L);
    assertThat(draft.contentLocale()).isEqualTo("zh-CN");
  }

  @Test
  void usesEnglishForServerNormalizedReviewCopy() {
    ObjectNode output = (ObjectNode) structuredOutput("""
        {
          "isCodeSubmission": true,
          "belongsToCurrentProblem": true,
          "isCompleteLeetCodeSolution": true,
          "language": "java",
          "rawCode": "class Solution {}",
          "normalizedCode": "class Solution {}",
          "evidence": [],
          "contextSummary": "The submission is incomplete.",
          "scores": {"correctness": 4, "complexity": 2, "edgeCases": 2, "codeQuality": 1, "problemFit": 1, "total": 10},
          "passed": true,
          "deductionReasons": [],
          "improvementSuggestions": ["Complete the implementation."],
          "reviewMarkdown": "The implementation needs work.",
          "affectedTagIds": []
        }
        """);
    ObjectNode assessment = (ObjectNode) output.path(PracticeCodeReviewConstants.JSON_JUDGE_ASSESSMENT);
    assessment.put(PracticeCodeReviewConstants.JSON_JUDGE_VERDICT, "TIME_LIMIT_EXCEEDED");
    assessment.put(PracticeCodeReviewConstants.JSON_BLOCKING_ISSUE, true);
    assessment.put(PracticeCodeReviewConstants.JSON_MEETS_EXPECTED_COMPLEXITY, false);

    PracticeCodeReviewDraft draft = mapper.map(context("en-US"), output).draft().orElseThrow();

    assertThat(draft.contentLocale()).isEqualTo("en-US");
    assertThat(draft.deductionReasons().get(0))
        .startsWith("Judge blocker:")
        .contains("time limit");
    assertThat(draft.deductionReasons()).noneMatch(reason -> reason.contains("评测阻断"));
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
          "reviewMarkdown": "通过。",
          "affectedTagIds": []
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
          "reviewMarkdown": "这不是当前题目的提交。",
          "affectedTagIds": []
        }
        """));

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.NOT_COMPLETE_SUBMISSION);
    assertThat(result.failureCode()).isNull();
    assertThat(result.draft()).isEmpty();
  }

  @Test
  void returnsNotCodeLikeWhenModelRejectsCodeSubmission() {
    PracticeReviewResult result = mapper.map(context(), structuredOutput("""
        {
          "isCodeSubmission": false,
          "belongsToCurrentProblem": true,
          "isCompleteLeetCodeSolution": true,
          "language": "java",
          "rawCode": "",
          "normalizedCode": "",
          "evidence": [],
          "contextSummary": "不是代码。",
          "scores": {"correctness": 0, "complexity": 0, "edgeCases": 0, "codeQuality": 0, "problemFit": 0, "total": 0},
          "passed": false,
          "deductionReasons": [],
          "improvementSuggestions": [],
          "reviewMarkdown": "",
          "affectedTagIds": []
        }
        """));

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.NOT_CODE_LIKE);
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
          "reviewMarkdown": "核心正确性不足。",
          "affectedTagIds": []
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
          "reviewMarkdown": "无效分数。",
          "affectedTagIds": []
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
          "reviewMarkdown": "无效判定字段。",
          "affectedTagIds": []
        }
        """));

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(PracticeReviewResult.INVALID_STRUCTURED_OUTPUT);
    assertThat(result.draft()).isEmpty();
  }

  @Test
  void capsTimeLimitExceededAsBlockingFailure() {
    PracticeReviewResult result = mapper.map(context(), structuredOutput("""
        {
          "isCodeSubmission": true,
          "belongsToCurrentProblem": true,
          "isCompleteLeetCodeSolution": true,
          "language": "java",
          "rawCode": "class Solution { int twoSum(int[] nums) { return 0; } }",
          "normalizedCode": "class Solution { int twoSum(int[] nums) { return 0; } }",
          "evidence": [],
          "contextSummary": "平方级枚举在最大约束下会超时。",
          "judgeAssessment": {
            "verdict": "TIME_LIMIT_EXCEEDED",
            "basis": "STATIC_ANALYSIS",
            "blockingIssue": true,
            "meetsExpectedComplexity": false,
            "timeComplexity": "O(n^2)",
            "spaceComplexity": "O(1)",
            "expectedTimeComplexity": "O(n)",
            "constraintAnalysis": "最大输入规模下需要平方级比较，预计超过时间限制。"
          },
          "scores": {
            "correctness": 4.0,
            "complexity": 2.0,
            "edgeCases": 2.0,
            "codeQuality": 1.0,
            "problemFit": 1.0,
            "total": 10.0
          },
          "passed": true,
          "deductionReasons": [],
          "improvementSuggestions": ["使用哈希表。"],
          "reviewMarkdown": "需要优化复杂度。",
          "affectedTagIds": []
        }
        """));

    PracticeCodeReviewDraft draft = result.draft().orElseThrow();
    assertThat(draft.score().correctness()).isEqualByComparingTo("2.0");
    assertThat(draft.score().complexity()).isEqualByComparingTo("0");
    assertThat(draft.score().total()).isEqualByComparingTo("5.0");
    assertThat(draft.passed()).isFalse();
    assertThat(draft.deductionReasons().get(0)).contains("评测阻断");
    assertThat(draft.evidence())
        .extracting(PracticeCodeReviewEvidence::type)
        .contains(
            PracticeCodeReviewConstants.EVIDENCE_JUDGE_VERDICT,
            PracticeCodeReviewConstants.EVIDENCE_JUDGE_BLOCKING_CAP);
  }

  @Test
  void capsClearlySuboptimalButLikelyAcceptedSolutionAtEight() {
    PracticeReviewResult result = mapper.map(context(), structuredOutput("""
        {
          "isCodeSubmission": true,
          "belongsToCurrentProblem": true,
          "isCompleteLeetCodeSolution": true,
          "language": "java",
          "rawCode": "class Solution { int twoSum(int[] nums) { return 0; } }",
          "normalizedCode": "class Solution { int twoSum(int[] nums) { return 0; } }",
          "evidence": [],
          "contextSummary": "暴力枚举预计可以通过，但没有达到目标复杂度。",
          "judgeAssessment": {
            "verdict": "LIKELY_ACCEPTED",
            "basis": "STATIC_ANALYSIS",
            "blockingIssue": false,
            "meetsExpectedComplexity": false,
            "timeComplexity": "O(n^2)",
            "spaceComplexity": "O(1)",
            "expectedTimeComplexity": "O(n)",
            "constraintAnalysis": "能够完成求解，但明显慢于哈希表方案。"
          },
          "scores": {
            "correctness": 4.0,
            "complexity": 2.0,
            "edgeCases": 2.0,
            "codeQuality": 1.0,
            "problemFit": 1.0,
            "total": 10.0
          },
          "passed": true,
          "deductionReasons": [],
          "improvementSuggestions": ["使用哈希表。"],
          "reviewMarkdown": "功能正确但复杂度不是目标解法。",
          "affectedTagIds": []
        }
        """));

    PracticeCodeReviewDraft draft = result.draft().orElseThrow();
    assertThat(draft.score().complexity()).isEqualByComparingTo("1.0");
    assertThat(draft.score().total()).isEqualByComparingTo("8.0");
    assertThat(draft.passed()).isTrue();
    assertThat(draft.deductionReasons().get(0)).contains("总分最高为 8.0");
    assertThat(draft.evidence())
        .extracting(PracticeCodeReviewEvidence::type)
        .contains(PracticeCodeReviewConstants.EVIDENCE_SUBOPTIMAL_COMPLEXITY_CAP);
  }

  private PracticeTurnContext context() {
    return context("zh-CN");
  }

  private PracticeTurnContext context(String locale) {
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
        locale);
  }

  private JsonNode structuredOutput(String json) {
    try {
      ObjectNode output = (ObjectNode) objectMapper.readTree(json);
      if (!output.has(PracticeCodeReviewConstants.JSON_JUDGE_ASSESSMENT)) {
        output.set(PracticeCodeReviewConstants.JSON_JUDGE_ASSESSMENT, defaultJudgeAssessment());
      }
      if (!output.has(PracticeCodeReviewConstants.JSON_SCORE_EXPLANATIONS)) {
        output.set(PracticeCodeReviewConstants.JSON_SCORE_EXPLANATIONS, defaultScoreExplanations());
      }
      return output;
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException(exception);
    }
  }

  private ObjectNode defaultJudgeAssessment() {
    ObjectNode assessment = objectMapper.createObjectNode();
    assessment.put(PracticeCodeReviewConstants.JSON_JUDGE_VERDICT, "LIKELY_ACCEPTED");
    assessment.put(PracticeCodeReviewConstants.JSON_VERDICT_BASIS, "STATIC_ANALYSIS");
    assessment.put(PracticeCodeReviewConstants.JSON_BLOCKING_ISSUE, false);
    assessment.put(PracticeCodeReviewConstants.JSON_MEETS_EXPECTED_COMPLEXITY, true);
    assessment.put(PracticeCodeReviewConstants.JSON_TIME_COMPLEXITY, "O(n)");
    assessment.put(PracticeCodeReviewConstants.JSON_SPACE_COMPLEXITY, "O(1)");
    assessment.put(PracticeCodeReviewConstants.JSON_EXPECTED_TIME_COMPLEXITY, "O(n)");
    assessment.put(PracticeCodeReviewConstants.JSON_CONSTRAINT_ANALYSIS, "最大约束下预计可以通过。");
    return assessment;
  }

  private ObjectNode defaultScoreExplanations() {
    ObjectNode explanations = objectMapper.createObjectNode();
    explanations.put(PracticeCodeReviewConstants.JSON_SCORE_CORRECTNESS, "核心逻辑基本正确。");
    explanations.put(PracticeCodeReviewConstants.JSON_SCORE_COMPLEXITY, "达到题目预期复杂度。");
    explanations.put(PracticeCodeReviewConstants.JSON_SCORE_EDGE_CASES, "覆盖了主要边界条件。");
    explanations.put(PracticeCodeReviewConstants.JSON_SCORE_CODE_QUALITY, "代码结构清晰。");
    explanations.put(PracticeCodeReviewConstants.JSON_SCORE_PROBLEM_FIT, "满足当前题目的主要要求。");
    return explanations;
  }
}
