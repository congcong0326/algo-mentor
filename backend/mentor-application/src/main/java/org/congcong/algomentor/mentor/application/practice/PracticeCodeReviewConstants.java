package org.congcong.algomentor.mentor.application.practice;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

public final class PracticeCodeReviewConstants {

  public static final String SCENARIO = "practice_code_review";
  public static final String SCHEMA_NAME = "practice_code_review_result";
  public static final String SCHEMA_VERSION = "v6";
  public static final String AGENT_TITLE = "practice-code-review";
  /**
   * 基于受信 sessionId 与 userMessageId 组成 Review child run 的稳定幂等键前缀。
   */
  public static final String CHILD_IDEMPOTENCY_KEY_PREFIX = "practice-code-review:";
  public static final String JSON_JUDGE_ASSESSMENT = "judgeAssessment";
  public static final String JSON_JUDGE_VERDICT = "verdict";
  public static final String JSON_VERDICT_BASIS = "basis";
  public static final String JSON_BLOCKING_ISSUE = "blockingIssue";
  public static final String JSON_MEETS_EXPECTED_COMPLEXITY = "meetsExpectedComplexity";
  public static final String JSON_TIME_COMPLEXITY = "timeComplexity";
  public static final String JSON_SPACE_COMPLEXITY = "spaceComplexity";
  public static final String JSON_EXPECTED_TIME_COMPLEXITY = "expectedTimeComplexity";
  public static final String JSON_CONSTRAINT_ANALYSIS = "constraintAnalysis";
  public static final String JSON_SCORE_EXPLANATIONS = "scoreExplanations";
  public static final String JSON_SCORE_CORRECTNESS = "correctness";
  public static final String JSON_SCORE_COMPLEXITY = "complexity";
  public static final String JSON_SCORE_EDGE_CASES = "edgeCases";
  public static final String JSON_SCORE_CODE_QUALITY = "codeQuality";
  public static final String JSON_SCORE_PROBLEM_FIT = "problemFit";
  public static final String JSON_AFFECTED_TAG_IDS = "affectedTagIds";
  /** 当前正式 Review 生成时的同题近期提交历程摘要。 */
  public static final String JSON_REVIEW_HISTORY_SUMMARY = "reviewHistorySummary";
  /** 历程摘要的存储和 structured output 最大长度。 */
  public static final int REVIEW_HISTORY_SUMMARY_MAX_LENGTH = 200;
  public static final BigDecimal PASS_SCORE = new BigDecimal("6.0");
  /** 存在正确性或评测阻断时，正确性分不得超过此值。 */
  public static final BigDecimal BLOCKING_CORRECTNESS_CAP = new BigDecimal("2.0");
  /** 存在正确性或评测阻断时，总分不得超过此值。 */
  public static final BigDecimal BLOCKING_TOTAL_CAP = new BigDecimal("5.0");
  /** 未达到题目预期复杂度但预计仍可通过时，复杂度分不得超过此值。 */
  public static final BigDecimal SUBOPTIMAL_COMPLEXITY_CAP = new BigDecimal("1.0");
  /** 未达到题目预期复杂度但预计仍可通过时，总分不得超过此值。 */
  public static final BigDecimal SUBOPTIMAL_TOTAL_CAP = new BigDecimal("8.0");
  /** 内存超限阻断下保留的最高复杂度分。 */
  public static final BigDecimal MEMORY_LIMIT_COMPLEXITY_CAP = new BigDecimal("0.5");
  /** 正确性评分允许的固定档位，避免模型输出无依据的任意精度。 */
  public static final List<BigDecimal> CORRECTNESS_SCORE_LEVELS = decimalLevels(
      "0", "0.5", "1", "1.5", "2", "2.5", "3", "3.5", "4");
  /** 复杂度评分允许的固定档位。 */
  public static final List<BigDecimal> COMPLEXITY_SCORE_LEVELS = decimalLevels(
      "0", "0.5", "1", "1.5", "2");
  /** 边界条件评分允许的固定档位。 */
  public static final List<BigDecimal> EDGE_CASE_SCORE_LEVELS = decimalLevels(
      "0", "0.5", "1", "1.5", "2");
  /** 代码质量使用等级映射后的固定分值。 */
  public static final List<BigDecimal> CODE_QUALITY_SCORE_LEVELS = decimalLevels(
      "0", "0.5", "0.75", "1");
  /** 题目要求符合度使用完全、部分、不符合三个固定档位。 */
  public static final List<BigDecimal> PROBLEM_FIT_SCORE_LEVELS = decimalLevels(
      "0", "0.5", "1");
  public static final String METADATA_CODE_REVIEW = "codeReview";
  /**
   * Review LLM 请求是否来自服务端判定的代码提交候选轮次。
   */
  public static final String METADATA_REVIEW_CANDIDATE = "reviewCandidate";
  public static final String EVIDENCE_CORRECTNESS_BLOCKING_CAP = "CORRECTNESS_BLOCKING_CAP";
  public static final String EVIDENCE_JUDGE_BLOCKING_CAP = "JUDGE_BLOCKING_CAP";
  public static final String EVIDENCE_SUBOPTIMAL_COMPLEXITY_CAP = "SUBOPTIMAL_COMPLEXITY_CAP";
  public static final String EVIDENCE_JUDGE_VERDICT = "JUDGE_VERDICT";
  public static final String EVIDENCE_TIME_COMPLEXITY = "TIME_COMPLEXITY";
  public static final String EVIDENCE_SPACE_COMPLEXITY = "SPACE_COMPLEXITY";
  public static final String EVIDENCE_EXPECTED_TIME_COMPLEXITY = "EXPECTED_TIME_COMPLEXITY";
  public static final String EVIDENCE_CONSTRAINT_ANALYSIS = "CONSTRAINT_ANALYSIS";
  public static final String EVIDENCE_SCORE_CORRECTNESS = "SCORE_CORRECTNESS";
  public static final String EVIDENCE_SCORE_COMPLEXITY = "SCORE_COMPLEXITY";
  public static final String EVIDENCE_SCORE_EDGE_CASES = "SCORE_EDGE_CASES";
  public static final String EVIDENCE_SCORE_CODE_QUALITY = "SCORE_CODE_QUALITY";
  public static final String EVIDENCE_SCORE_PROBLEM_FIT = "SCORE_PROBLEM_FIT";

  private PracticeCodeReviewConstants() {
  }

  private static List<BigDecimal> decimalLevels(String... values) {
    return Arrays.stream(values).map(BigDecimal::new).toList();
  }
}
