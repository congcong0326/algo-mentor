package org.congcong.algomentor.mentor.application.practice;

import java.math.BigDecimal;

public final class PracticeCodeReviewConstants {

  public static final String SCENARIO = "practice_code_review";
  public static final String SCHEMA_NAME = "practice_code_review_result";
  public static final String SCHEMA_VERSION = "v3";
  public static final String JSON_JUDGE_ASSESSMENT = "judgeAssessment";
  public static final String JSON_JUDGE_VERDICT = "verdict";
  public static final String JSON_VERDICT_BASIS = "basis";
  public static final String JSON_BLOCKING_ISSUE = "blockingIssue";
  public static final String JSON_MEETS_EXPECTED_COMPLEXITY = "meetsExpectedComplexity";
  public static final String JSON_TIME_COMPLEXITY = "timeComplexity";
  public static final String JSON_SPACE_COMPLEXITY = "spaceComplexity";
  public static final String JSON_EXPECTED_TIME_COMPLEXITY = "expectedTimeComplexity";
  public static final String JSON_CONSTRAINT_ANALYSIS = "constraintAnalysis";
  public static final String JSON_AFFECTED_TAG_IDS = "affectedTagIds";
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

  private PracticeCodeReviewConstants() {
  }
}
