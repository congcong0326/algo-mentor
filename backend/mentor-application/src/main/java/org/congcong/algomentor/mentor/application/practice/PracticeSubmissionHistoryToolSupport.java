package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentExecutionContext;

/** 三个历史提交 Tool 共享的严格参数校验和不含业务数据的失败结果。 */
final class PracticeSubmissionHistoryToolSupport {

  private PracticeSubmissionHistoryToolSupport() {
  }

  static String scopeRef(AgentExecutionContext context) {
    if (context == null || context.requestMetadata() == null) {
      return "";
    }
    Object value = context.requestMetadata().get(PracticeSubmissionHistoryToolContracts.METADATA_SCOPE_REF);
    return value == null ? "" : value.toString().trim();
  }

  static boolean codeDetailIntent(AgentExecutionContext context) {
    return context != null && context.requestMetadata() != null
        && Boolean.TRUE.equals(context.requestMetadata().get(
            PracticeSubmissionHistoryToolContracts.METADATA_CODE_DETAIL_INTENT));
  }

  static boolean hasOnlyFields(JsonNode arguments, Set<String> allowedFields) {
    if (arguments == null || !arguments.isObject()) {
      return false;
    }
    Iterator<String> fields = arguments.fieldNames();
    while (fields.hasNext()) {
      if (!allowedFields.contains(fields.next())) {
        return false;
      }
    }
    return true;
  }

  static String requiredText(JsonNode arguments, String field) {
    JsonNode value = arguments == null ? null : arguments.get(field);
    return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
  }

  static String optionalText(JsonNode arguments, String field) {
    JsonNode value = arguments == null ? null : arguments.get(field);
    return value == null || value.isNull() ? "" : value.isTextual() ? value.asText().trim() : null;
  }

  static int optionalListLimit(JsonNode arguments) {
    JsonNode value = arguments == null ? null : arguments.get(PracticeSubmissionHistoryToolContracts.ARGUMENT_LIMIT);
    if (value == null || value.isNull()) {
      return PracticeSubmissionHistoryToolContracts.DEFAULT_LIST_LIMIT;
    }
    if (!value.canConvertToInt()) {
      return -1;
    }
    int limit = value.intValue();
    return limit >= 1 && limit <= PracticeSubmissionHistoryToolContracts.MAX_LIST_LIMIT ? limit : -1;
  }

  static ObjectNode success(String type) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put(PracticeSubmissionHistoryToolContracts.FIELD_TYPE, type);
    result.put(PracticeSubmissionHistoryToolContracts.FIELD_STATUS, PracticeSubmissionHistoryToolContracts.STATUS_OK);
    return result;
  }

  static ObjectNode failure(String type, String status, String failureCode) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put(PracticeSubmissionHistoryToolContracts.FIELD_TYPE, type);
    result.put(PracticeSubmissionHistoryToolContracts.FIELD_STATUS, status);
    result.put(PracticeSubmissionHistoryToolContracts.FIELD_FAILURE_CODE, failureCode);
    result.put(PracticeSubmissionHistoryToolContracts.FIELD_MESSAGE, stableMessage(failureCode));
    return result;
  }

  static ObjectNode failureForScope(String type, PracticeSubmissionHistoryRunScopeRegistry.ScopeUseStatus status) {
    return status == PracticeSubmissionHistoryRunScopeRegistry.ScopeUseStatus.BUDGET_EXHAUSTED
        ? failure(type, PracticeSubmissionHistoryToolContracts.STATUS_BUDGET_EXHAUSTED,
            PracticeSubmissionHistoryToolContracts.FAILURE_BUDGET_EXHAUSTED)
        : failure(type, PracticeSubmissionHistoryToolContracts.STATUS_UNAVAILABLE,
            PracticeSubmissionHistoryToolContracts.FAILURE_UNAVAILABLE);
  }

  static void writeProblem(ObjectNode target, PracticeSubmissionHistoryRunScopeRegistry.ScopedProblem problem) {
    target.put(PracticeSubmissionHistoryToolContracts.FIELD_PROBLEM_REF, problem.problemRef());
    target.put(PracticeSubmissionHistoryToolContracts.FIELD_TITLE, problem.title());
    writeStrings(target.putArray(PracticeSubmissionHistoryToolContracts.FIELD_TAGS), problem.tags());
  }

  static void writeSubmission(
      ObjectNode target,
      String submissionRef,
      PracticeSubmissionHistoryReview review,
      List<String> affectedTags,
      boolean includeScoreBreakdown
  ) {
    if (submissionRef != null && !submissionRef.isBlank()) {
      target.put(PracticeSubmissionHistoryToolContracts.FIELD_SUBMISSION_REF, submissionRef);
    }
    target.put(PracticeSubmissionHistoryToolContracts.FIELD_SUBMITTED_AT, review.submittedAt().toString());
    target.put(PracticeSubmissionHistoryToolContracts.FIELD_LANGUAGE, review.language());
    target.put(PracticeSubmissionHistoryToolContracts.FIELD_TOTAL_SCORE, review.totalScore());
    target.put(PracticeSubmissionHistoryToolContracts.FIELD_PASSED, review.passed());
    if (includeScoreBreakdown) {
      ObjectNode scoreBreakdown = target.putObject(PracticeSubmissionHistoryToolContracts.FIELD_SCORE_BREAKDOWN);
      scoreBreakdown.put(PracticeSubmissionHistoryToolContracts.FIELD_CORRECTNESS, review.correctnessScore());
      scoreBreakdown.put(PracticeSubmissionHistoryToolContracts.FIELD_COMPLEXITY, review.complexityScore());
      scoreBreakdown.put(PracticeSubmissionHistoryToolContracts.FIELD_EDGE_CASES, review.edgeCaseScore());
      scoreBreakdown.put(PracticeSubmissionHistoryToolContracts.FIELD_CODE_QUALITY, review.codeQualityScore());
      scoreBreakdown.put(PracticeSubmissionHistoryToolContracts.FIELD_PROBLEM_FIT, review.problemFitScore());
    }
    writeStrings(target.putArray(PracticeSubmissionHistoryToolContracts.FIELD_DEDUCTION_REASONS),
        boundedFeedback(review.deductionReasons()));
    writeStrings(target.putArray(PracticeSubmissionHistoryToolContracts.FIELD_IMPROVEMENT_SUGGESTIONS),
        boundedFeedback(review.improvementSuggestions()));
    writeStrings(target.putArray(PracticeSubmissionHistoryToolContracts.FIELD_AFFECTED_TAGS), affectedTags);
    if (review.reviewHistorySummary() != null) {
      target.put(PracticeSubmissionHistoryToolContracts.FIELD_REVIEW_HISTORY_SUMMARY, review.reviewHistorySummary());
    }
  }

  static void writeOverviewLatestSubmission(
      ObjectNode target,
      String submissionRef,
      PracticeSubmissionHistoryReview review
  ) {
    target.put(PracticeSubmissionHistoryToolContracts.FIELD_SUBMISSION_REF, submissionRef);
    target.put(PracticeSubmissionHistoryToolContracts.FIELD_SUBMITTED_AT, review.submittedAt().toString());
    target.put(PracticeSubmissionHistoryToolContracts.FIELD_LANGUAGE, review.language());
    target.put(PracticeSubmissionHistoryToolContracts.FIELD_TOTAL_SCORE, review.totalScore());
    target.put(PracticeSubmissionHistoryToolContracts.FIELD_PASSED, review.passed());
    if (review.reviewHistorySummary() != null) {
      target.put(PracticeSubmissionHistoryToolContracts.FIELD_REVIEW_HISTORY_SUMMARY, review.reviewHistorySummary());
    }
  }

  static int initialVisibleChars(ObjectNode result, int inlineMaxChars, int previewMaxChars) {
    int chars = result.toString().length();
    return chars <= inlineMaxChars ? chars : Math.min(chars, previewMaxChars);
  }

  private static List<String> boundedFeedback(List<String> values) {
    return values == null ? List.of() : values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(PracticeSubmissionHistoryToolSupport::boundedText)
        .limit(PracticeSubmissionHistoryToolContracts.MAX_FEEDBACK_ITEMS)
        .toList();
  }

  private static String boundedText(String value) {
    String normalized = value.replaceAll("\\s+", " ").trim();
    int max = PracticeSubmissionHistoryToolContracts.MAX_FEEDBACK_ITEM_CHARS;
    return normalized.length() <= max ? normalized : normalized.substring(0, max - 1) + "…";
  }

  private static void writeStrings(ArrayNode target, List<String> values) {
    if (values != null) {
      values.forEach(target::add);
    }
  }

  private static String stableMessage(String failureCode) {
    return switch (failureCode) {
      case PracticeSubmissionHistoryToolContracts.FAILURE_INVALID_ARGUMENTS -> "Tool arguments are invalid.";
      case PracticeSubmissionHistoryToolContracts.FAILURE_BUDGET_EXHAUSTED ->
          "Practice submission history read budget is exhausted.";
      case PracticeSubmissionHistoryToolContracts.FAILURE_USER_INTENT_REQUIRED ->
          "Explicit old-code review intent is required.";
      case PracticeSubmissionHistoryToolContracts.FAILURE_UNAVAILABLE ->
          "Requested practice submission history is unavailable in the current run.";
      default -> "Practice submission history lookup failed.";
    };
  }
}
