package org.congcong.algomentor.mentor.application.practice;

import java.math.BigDecimal;

public final class PracticeCodeReviewConstants {

  public static final String SCENARIO = "practice_code_review";
  public static final String SCHEMA_NAME = "practice_code_review_result";
  public static final String SCHEMA_VERSION = "v1";
  public static final BigDecimal PASS_SCORE = new BigDecimal("6.0");
  public static final String METADATA_CODE_REVIEW = "codeReview";
  /**
   * Review LLM 请求是否来自服务端判定的代码提交候选轮次。
   */
  public static final String METADATA_REVIEW_CANDIDATE = "reviewCandidate";
  public static final String EVIDENCE_CORRECTNESS_BLOCKING_CAP = "CORRECTNESS_BLOCKING_CAP";

  private PracticeCodeReviewConstants() {
  }
}
