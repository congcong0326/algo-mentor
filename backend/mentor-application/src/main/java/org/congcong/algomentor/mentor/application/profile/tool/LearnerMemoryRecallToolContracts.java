package org.congcong.algomentor.mentor.application.profile.tool;

import java.util.Set;

/** Practice Chat run-local 记忆探索工具的稳定名称、字段与预算契约。 */
public final class LearnerMemoryRecallToolContracts {

  public static final String SEARCH_LEARNER_MEMORY = "search_learner_memory";
  public static final String READ_LEARNER_MEMORY_SECTION = "read_learner_memory_section";
  public static final String GET_LEARNER_MEMORY_EVIDENCE = "get_learner_memory_evidence";
  public static final Set<String> TOOL_NAMES = Set.of(
      SEARCH_LEARNER_MEMORY,
      READ_LEARNER_MEMORY_SECTION,
      GET_LEARNER_MEMORY_EVIDENCE);

  public static final String ARGUMENT_QUERY = "query";
  public static final String ARGUMENT_SECTION_REF = "sectionRef";
  public static final String ARGUMENT_TAG_VALUES = "tagValues";
  public static final String ARGUMENT_LIMIT = "limit";
  public static final String ARGUMENT_CURSOR = "cursor";
  public static final String ARGUMENT_AFTER_STATEMENT_REF = "afterStatementRef";
  public static final String ARGUMENT_STATEMENT_REF = "statementRef";

  public static final String FIELD_TYPE = "type";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_FAILURE_CODE = "failureCode";
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_ITEMS = "items";
  public static final String FIELD_NEXT_CURSOR = "nextCursor";
  public static final String FIELD_SECTION_REF = "sectionRef";
  public static final String FIELD_SECTION_TITLE = "sectionTitle";
  public static final String FIELD_STATEMENT_REF = "statementRef";
  public static final String FIELD_CLAIM_TEXT = "claimText";
  public static final String FIELD_SOURCE_SUMMARY = "sourceSummary";
  public static final String FIELD_CURRENT_PROBLEM_MATCH = "currentProblemMatch";
  public static final String FIELD_EVIDENCE_ROLE = "evidenceRole";
  public static final String FIELD_RECORDED_AT = "recordedAt";
  public static final String FIELD_EVIDENCE_SOURCE = "source";

  public static final String TYPE_SEARCH = "learner_memory_search";
  public static final String TYPE_SECTION = "learner_memory_section";
  public static final String TYPE_EVIDENCE = "learner_memory_evidence";
  public static final String STATUS_OK = "OK";
  public static final String STATUS_BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED";
  public static final String STATUS_FAILED = "FAILED";
  public static final String FAILURE_INVALID_ARGUMENTS = "INVALID_ARGUMENTS";
  public static final String FAILURE_NOT_FOUND_OR_NOT_READABLE = "NOT_FOUND_OR_NOT_READABLE";
  public static final String FAILURE_SCOPE_UNAVAILABLE = "SCOPE_UNAVAILABLE";
  public static final String FAILURE_BUDGET_EXHAUSTED = "BUDGET_EXHAUSTED";
  public static final String FAILURE_INTERNAL = "INTERNAL";
  public static final String MESSAGE_EMPTY_SEARCH = "当前 run 快照未找到匹配项。";

  public static final String CURSOR_SEARCH = "search";
  public static final String CURSOR_EVIDENCE = "evidence";
  public static final int MAX_ITEMS = 20;
  public static final int MAX_BUSINESS_TOOL_CALLS = 3;
  public static final int MAX_RESULT_READS = 2;
  public static final int MAX_RESULT_CHARS = 8_000;
  public static final int MAX_TOTAL_VISIBLE_CHARS = 24_000;

  private LearnerMemoryRecallToolContracts() {
  }
}
