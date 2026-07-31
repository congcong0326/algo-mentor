package org.congcong.algomentor.mentor.application.practice;

/** 当前题学习状态工具的稳定名称、参数、结果字段和状态契约。 */
public final class PracticeLearningStateAgentToolContracts {

  public static final String TOOL_NAME = "get_current_problem_learning_state";
  public static final String ARGUMENT_INCLUDE_NOTE_BODY = "includeNoteBody";

  public static final String RESULT_TYPE = "current_problem_learning_state";
  public static final String STATUS_OK = "OK";
  public static final String STATUS_FAILED = "FAILED";

  public static final String FAILURE_INVALID_ARGUMENTS = "INVALID_ARGUMENTS";
  public static final String FAILURE_MISSING_METADATA = "MISSING_METADATA";
  public static final String FAILURE_NOT_PRACTICE_CHAT = "NOT_PRACTICE_CHAT";
  public static final String FAILURE_PRACTICE_SESSION_NOT_FOUND = "PRACTICE_SESSION_NOT_FOUND";
  public static final String FAILURE_CURRENT_PROBLEM_MISMATCH = "CURRENT_PROBLEM_MISMATCH";
  public static final String FAILURE_INTERNAL = "INTERNAL";

  public static final String NOTE_BODY_NOT_REQUESTED = "NOT_REQUESTED";
  public static final String NOTE_BODY_INCLUDED = "INCLUDED";
  public static final String NOTE_BODY_EMPTY = "EMPTY";
  public static final String NOTE_BODY_EXPLICIT_REQUEST_REQUIRED = "EXPLICIT_REQUEST_REQUIRED";

  public static final String FIELD_TYPE = "type";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_FAILURE_CODE = "failureCode";
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_PROBLEM_SLUG = "problemSlug";
  public static final String FIELD_PRACTICE = "practice";
  public static final String FIELD_PROGRESS_STATUS = "progressStatus";
  public static final String FIELD_COMPLETED = "completed";
  public static final String FIELD_SKIPPED = "skipped";
  public static final String FIELD_UPDATED_AT = "updatedAt";
  public static final String FIELD_LATEST_FORMAL_REVIEW = "latestFormalReview";
  public static final String FIELD_EXISTS = "exists";
  public static final String FIELD_REVIEW_ID = "reviewId";
  public static final String FIELD_VERSION_NO = "versionNo";
  public static final String FIELD_CREATED_AT = "createdAt";
  public static final String FIELD_TOTAL_SCORE = "totalScore";
  public static final String FIELD_PASSED = "passed";
  public static final String FIELD_DEDUCTION_REASONS = "deductionReasons";
  public static final String FIELD_IMPROVEMENT_SUGGESTIONS = "improvementSuggestions";
  public static final String FIELD_AFFECTED_TAG_IDS = "affectedTagIds";
  public static final String FIELD_REVIEW_SCHEDULE = "reviewSchedule";
  public static final String FIELD_ARCHIVED = "archived";
  public static final String FIELD_DUE_AT = "dueAt";
  public static final String FIELD_LAST_REVIEWED_AT = "lastReviewedAt";
  public static final String FIELD_LAST_RATING = "lastRating";
  public static final String FIELD_FSRS_STATE = "fsrsState";
  public static final String FIELD_REPETITIONS = "repetitions";
  public static final String FIELD_INTERVAL_DAYS = "intervalDays";
  public static final String FIELD_LAPSES = "lapses";
  public static final String FIELD_NOTE = "note";
  public static final String FIELD_HAS_CONTENT = "hasContent";
  public static final String FIELD_REVISION = "revision";
  public static final String FIELD_OUTLINE = "outline";
  public static final String FIELD_SCHEMA_VERSION = "schemaVersion";
  public static final String FIELD_CORE_IDEA = "coreIdea";
  public static final String FIELD_DATA_STRUCTURES = "dataStructures";
  public static final String FIELD_CUSTOM_DATA_STRUCTURES = "customDataStructures";
  public static final String FIELD_DATA_STRUCTURE_NOTES = "dataStructureNotes";
  public static final String FIELD_ALGORITHMS = "algorithms";
  public static final String FIELD_CUSTOM_ALGORITHMS = "customAlgorithms";
  public static final String FIELD_ALGORITHM_NOTES = "algorithmNotes";
  public static final String FIELD_TIME_COMPLEXITY = "timeComplexity";
  public static final String FIELD_SPACE_COMPLEXITY = "spaceComplexity";
  public static final String FIELD_EDGE_CASES = "edgeCases";
  public static final String FIELD_KEY = "key";
  public static final String FIELD_CUSTOM_TEXT = "customText";
  public static final String FIELD_NOTE_BODY_STATUS = "noteBodyStatus";
  public static final String FIELD_NOTE_BODY_INCLUDED = "noteBodyIncluded";
  public static final String FIELD_NOTE_MARKDOWN = "noteMarkdown";

  private PracticeLearningStateAgentToolContracts() {
  }
}
