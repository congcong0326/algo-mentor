package org.congcong.algomentor.mentor.application.profile.tool;

/** Code Review 记忆更新只读工具的稳定名称、JSON 和 metadata 契约。 */
public final class LearnerMemoryAgentToolContracts {

  public static final String GET_PROBLEM_REVIEW_TRAJECTORY = "get_problem_review_trajectory";
  public static final String GET_CODE_REVIEW_EVIDENCE = "get_code_review_evidence";
  public static final String COMPARE_SUBMISSION_VERSIONS = "compare_submission_versions";

  public static final String ARGUMENT_PROBLEM_SLUG = "problemSlug";
  public static final String ARGUMENT_REVIEW_ID = "reviewId";
  public static final String ARGUMENT_FROM_REVIEW_ID = "fromReviewId";
  public static final String ARGUMENT_TO_REVIEW_ID = "toReviewId";

  public static final String METADATA_SCOPE_REF = "learnerMemoryScopeRef";
  public static final String METADATA_TOOL_CALL_COUNT = "learnerMemoryToolCallCount";

  public static final String RESULT_FIELD_TYPE = "type";
  public static final String RESULT_FIELD_STATUS = "status";
  public static final String RESULT_FIELD_FAILURE_CODE = "failureCode";
  public static final String RESULT_FIELD_MESSAGE = "message";
  public static final String RESULT_FIELD_TRUNCATED = "truncated";
  public static final String RESULT_FIELD_PROBLEM_SLUG = "problemSlug";
  public static final String RESULT_FIELD_REVIEW_ID = "reviewId";
  public static final String RESULT_FIELD_VERSION_NO = "versionNo";
  public static final String RESULT_FIELD_CREATED_AT = "createdAt";
  public static final String RESULT_FIELD_SCORE = "score";
  public static final String RESULT_FIELD_PASSED = "passed";
  public static final String RESULT_FIELD_DEDUCTION_REASONS = "deductionReasons";
  public static final String RESULT_FIELD_IMPROVEMENT_SUGGESTIONS = "improvementSuggestions";
  public static final String RESULT_FIELD_AFFECTED_TAG_IDS = "affectedTagIds";
  public static final String RESULT_FIELD_DETECTION_EVIDENCE = "detectionEvidence";
  public static final String RESULT_FIELD_CONTEXT_SUMMARY = "contextSummary";
  public static final String RESULT_FIELD_REVIEW = "review";
  public static final String RESULT_FIELD_VERSIONS = "versions";
  public static final String RESULT_FIELD_SCORE_DELTA = "scoreDelta";
  public static final String RESULT_FIELD_PERSISTED_FINDINGS = "persistedFindings";
  public static final String RESULT_FIELD_RESOLVED_FINDINGS = "resolvedFindings";
  public static final String RESULT_FIELD_NEW_FINDINGS = "newFindings";
  public static final String RESULT_FIELD_FROM_REVIEW_ID = "fromReviewId";
  public static final String RESULT_FIELD_TO_REVIEW_ID = "toReviewId";
  public static final String RESULT_FIELD_UNIFIED_DIFF = "unifiedDiff";
  public static final String RESULT_FIELD_EVIDENCE_TYPE = "type";
  public static final String RESULT_FIELD_EVIDENCE_VALUE = "value";
  public static final String RESULT_FIELD_SCORE_CORRECTNESS = "correctness";
  public static final String RESULT_FIELD_SCORE_COMPLEXITY = "complexity";
  public static final String RESULT_FIELD_SCORE_EDGE_CASES = "edgeCases";
  public static final String RESULT_FIELD_SCORE_CODE_QUALITY = "codeQuality";
  public static final String RESULT_FIELD_SCORE_PROBLEM_FIT = "problemFit";
  public static final String RESULT_FIELD_SCORE_TOTAL = "total";

  public static final String RESULT_TYPE_REVIEW_TRAJECTORY = "learner_memory_review_trajectory";
  public static final String RESULT_TYPE_REVIEW_EVIDENCE = "learner_memory_review_evidence";
  public static final String RESULT_TYPE_SUBMISSION_DIFF = "learner_memory_submission_diff";

  public static final String STATUS_OK = "OK";
  public static final String STATUS_FAILED = "FAILED";
  public static final String STATUS_BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED";

  public static final String FAILURE_INVALID_ARGUMENTS = "INVALID_ARGUMENTS";
  public static final String FAILURE_SCOPE_UNAVAILABLE = "SCOPE_UNAVAILABLE";
  public static final String FAILURE_SCOPE_FORBIDDEN = "SCOPE_FORBIDDEN";
  public static final String FAILURE_TOOL_ALREADY_USED = "TOOL_ALREADY_USED";
  public static final String FAILURE_BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED";
  public static final String FAILURE_REVIEW_NOT_FOUND = "REVIEW_NOT_FOUND";
  public static final String FAILURE_REVIEW_DATA_UNAVAILABLE = "REVIEW_DATA_UNAVAILABLE";
  public static final String FAILURE_INTERNAL = "INTERNAL";

  public static final int MAX_TOOL_CALLS = 3;
  public static final int MAX_TOOL_RESULT_CHARS = 8_000;

  private LearnerMemoryAgentToolContracts() {
  }
}
