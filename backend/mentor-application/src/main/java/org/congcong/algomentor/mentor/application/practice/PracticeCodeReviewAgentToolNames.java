package org.congcong.algomentor.mentor.application.practice;

/**
 * Practice Code Review agent tool 的名称、参数和结果 JSON 字段稳定契约。
 */
public final class PracticeCodeReviewAgentToolNames {

  public static final String SUBMIT_PRACTICE_CODE_REVIEW = "submit_practice_code_review";

  public static final String ARGUMENT_USER_INTENT = "userIntent";
  public static final String ARGUMENT_NOTES = "notes";

  public static final String RESULT_TYPE_PRACTICE_CODE_REVIEW_SUBMITTED = "practice_code_review_submitted";

  public static final String RESULT_TYPE = "type";
  public static final String RESULT_STATUS = "status";
  public static final String RESULT_REVIEW_ID = "reviewId";
  public static final String RESULT_VERSION_NO = "versionNo";
  public static final String RESULT_TOTAL_SCORE = "totalScore";
  public static final String RESULT_PASSED = "passed";
  public static final String RESULT_FAILURE_CODE = "failureCode";
  public static final String RESULT_PROBLEM_SLUG = "problemSlug";
  public static final String RESULT_SESSION_ID = "sessionId";
  public static final String RESULT_USER_MESSAGE_ID = "userMessageId";
  public static final String RESULT_AGENT_RUN_DB_ID = "agentRunDbId";
  public static final String RESULT_MESSAGE = "message";

  public static final String PREVIEW_PROBLEM_SLUG = "problemSlug";
  public static final String PREVIEW_PROBLEM_TITLE = "problemTitle";
  public static final String PREVIEW_LANGUAGE_HINT = "languageHint";
  public static final String PREVIEW_CODE_LENGTH = "codeLength";
  public static final String PREVIEW_CODE_PREVIEW = "codePreview";
  public static final String PREVIEW_EFFECTS = "effects";
  public static final String PREVIEW_CONTEXT_AVAILABLE = "contextAvailable";

  private PracticeCodeReviewAgentToolNames() {
  }
}
