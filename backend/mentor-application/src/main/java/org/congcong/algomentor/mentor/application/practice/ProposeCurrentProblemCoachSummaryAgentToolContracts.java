package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.review.ReviewContractConstants;

/** Practice Chat 教练总结候选 Tool 的稳定参数与结果契约。 */
public final class ProposeCurrentProblemCoachSummaryAgentToolContracts {

  public static final String TOOL_NAME = "propose_current_problem_coach_summary";
  public static final String ARGUMENT_SUMMARY_MARKDOWN = "summaryMarkdown";
  public static final String RESULT_TYPE = "current_problem_coach_summary_proposed";
  public static final String STATUS_PROPOSED = "PROPOSED";
  public static final String STATUS_FAILED = "FAILED";
  public static final String FIELD_TYPE = "type";
  public static final String FIELD_STATUS = "status";
  public static final String FIELD_FAILURE_CODE = "failureCode";
  public static final String FIELD_MESSAGE = "message";
  public static final String FIELD_PROPOSAL_ID = "proposalId";
  public static final String FIELD_PROBLEM_SLUG = "problemSlug";
  public static final String FIELD_SUMMARY_MARKDOWN = "summaryMarkdown";
  public static final String FIELD_OPERATION = "operation";
  public static final String FIELD_BASE_COACH_SUMMARY_REVISION = "baseCoachSummaryRevision";
  public static final String FAILURE_INVALID_ARGUMENTS = "INVALID_ARGUMENTS";
  public static final String FAILURE_MISSING_METADATA = "MISSING_TRUSTED_METADATA";
  public static final String FAILURE_NOT_PRACTICE_CHAT = "NOT_PRACTICE_CHAT";
  public static final String FAILURE_PRACTICE_SESSION_NOT_FOUND = "PRACTICE_SESSION_NOT_FOUND";
  public static final String FAILURE_CURRENT_PROBLEM_MISMATCH = "CURRENT_PROBLEM_MISMATCH";
  public static final String FAILURE_INTERNAL = "INTERNAL_ERROR";

  private ProposeCurrentProblemCoachSummaryAgentToolContracts() {
  }

  public static Optional<String> summaryMarkdown(JsonNode arguments) {
    if (arguments == null || !arguments.isObject() || arguments.size() != 1) {
      return Optional.empty();
    }
    JsonNode value = arguments.get(ARGUMENT_SUMMARY_MARKDOWN);
    if (value == null || !value.isTextual()) {
      return Optional.empty();
    }
    String normalized = value.textValue().strip();
    if (normalized.isEmpty() || normalized.length() > ReviewContractConstants.NOTE_MARKDOWN_MAX_CHARS) {
      return Optional.empty();
    }
    return Optional.of(normalized);
  }
}
