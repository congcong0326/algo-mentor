package org.congcong.algomentor.mentor.application.practice;

import java.util.Set;

/** Practice Chat 历史正式提交 Tool 的名称、入参与结果字段契约。 */
public final class PracticeSubmissionHistoryToolContracts {

  public static final String GET_PRACTICED_PROBLEM_OVERVIEW = "get_practiced_problem_overview";
  public static final String LIST_PRACTICE_PROBLEM_SUBMISSIONS = "list_practice_problem_submissions";
  public static final String READ_PRACTICE_SUBMISSION_DETAIL = "read_practice_submission_detail";
  public static final Set<String> TOOL_NAMES = Set.of(
      GET_PRACTICED_PROBLEM_OVERVIEW,
      LIST_PRACTICE_PROBLEM_SUBMISSIONS,
      READ_PRACTICE_SUBMISSION_DETAIL);

  /** request metadata 中的 run-local scope 引用。 */
  public static final String METADATA_SCOPE_REF = "practiceSubmissionHistoryScopeRef";
  /** request metadata 中由服务端解析的旧代码查看意图。 */
  public static final String METADATA_CODE_DETAIL_INTENT = "practiceSubmissionHistoryCodeDetailIntent";

  public static final String ARGUMENT_PROBLEM_REF = "problemRef";
  public static final String ARGUMENT_SUBMISSION_REF = "submissionRef";
  public static final String ARGUMENT_CURSOR = "cursor";
  public static final String ARGUMENT_LIMIT = "limit";

  public static final String FIELD_TYPE = "type";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_FAILURE_CODE = "failureCode";
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_PROBLEM = "problem";
  public static final String FIELD_PROBLEM_REF = "problemRef";
  public static final String FIELD_SUBMISSION_REF = "submissionRef";
  public static final String FIELD_SUBMISSIONS = "submissions";
  public static final String FIELD_SUBMISSION = "submission";
  public static final String FIELD_HAS_MORE = "hasMore";
  public static final String FIELD_NEXT_CURSOR = "nextCursor";
  public static final String FIELD_TITLE = "title";
  public static final String FIELD_TAGS = "tags";
  public static final String FIELD_FORMAL_SUBMISSION_COUNT = "formalSubmissionCount";
  public static final String FIELD_PASSED_SUBMISSION_COUNT = "passedSubmissionCount";
  public static final String FIELD_FIRST_SUBMITTED_AT = "firstSubmittedAt";
  public static final String FIELD_LATEST_SUBMISSION = "latestSubmission";
  public static final String FIELD_SUBMITTED_AT = "submittedAt";
  public static final String FIELD_LANGUAGE = "language";
  public static final String FIELD_TOTAL_SCORE = "totalScore";
  public static final String FIELD_PASSED = "passed";
  public static final String FIELD_DEDUCTION_REASONS = "deductionReasons";
  public static final String FIELD_IMPROVEMENT_SUGGESTIONS = "improvementSuggestions";
  public static final String FIELD_AFFECTED_TAGS = "affectedTags";
  public static final String FIELD_REVIEW_HISTORY_SUMMARY = "reviewHistorySummary";
  public static final String FIELD_REVIEWED_CODE = "reviewedCode";
  public static final String FIELD_REVIEW = "review";
  public static final String FIELD_SCORE_BREAKDOWN = "scoreBreakdown";
  public static final String FIELD_CORRECTNESS = "correctness";
  public static final String FIELD_COMPLEXITY = "complexity";
  public static final String FIELD_EDGE_CASES = "edgeCases";
  public static final String FIELD_CODE_QUALITY = "codeQuality";
  public static final String FIELD_PROBLEM_FIT = "problemFit";

  public static final String TYPE_OVERVIEW = "practiced_problem_overview";
  public static final String TYPE_SUBMISSION_LIST = "practice_problem_submission_list";
  public static final String TYPE_SUBMISSION_DETAIL = "practice_submission_detail";

  public static final String STATUS_OK = "OK";
  public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
  public static final String STATUS_BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED";
  public static final String STATUS_USER_INTENT_REQUIRED = "USER_INTENT_REQUIRED";
  public static final String STATUS_FAILED = "FAILED";

  public static final String FAILURE_INVALID_ARGUMENTS = "INVALID_ARGUMENTS";
  public static final String FAILURE_UNAVAILABLE = "UNAVAILABLE";
  public static final String FAILURE_BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED";
  public static final String FAILURE_USER_INTENT_REQUIRED = "USER_INTENT_REQUIRED";
  public static final String FAILURE_DATA_ACCESS = "DATA_ACCESS_FAILED";

  public static final int DEFAULT_LIST_LIMIT = 3;
  public static final int MAX_LIST_LIMIT = 5;
  public static final int MAX_FEEDBACK_ITEMS = 3;
  public static final int MAX_FEEDBACK_ITEM_CHARS = 500;
  public static final int MAX_OVERVIEW_AND_LIST_CALLS = 2;
  public static final int MAX_DETAIL_CALLS = 1;
  public static final int MAX_DETAIL_RESULT_READS = 2;
  public static final int MAX_DETAIL_VISIBLE_CHARS = 16_000;

  private PracticeSubmissionHistoryToolContracts() {
  }
}
