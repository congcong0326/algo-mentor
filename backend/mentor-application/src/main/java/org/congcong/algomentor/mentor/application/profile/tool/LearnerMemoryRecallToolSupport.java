package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallSnapshot;

/** 三项 run-local 记忆工具的参数、受控失败和完整项分页辅助。 */
final class LearnerMemoryRecallToolSupport {

  private LearnerMemoryRecallToolSupport() {
  }

  static String scopeRef(AgentExecutionContext context) {
    if (context == null || context.requestMetadata() == null) {
      return "";
    }
    Object value = context.requestMetadata().get(LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF);
    return value == null ? "" : value.toString().trim();
  }

  static ObjectNode success(String type) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put(LearnerMemoryRecallToolContracts.FIELD_TYPE, type);
    result.put(LearnerMemoryRecallToolContracts.FIELD_STATUS, LearnerMemoryRecallToolContracts.STATUS_OK);
    return result;
  }

  static ObjectNode failure(String type, String status, String code) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put(LearnerMemoryRecallToolContracts.FIELD_TYPE, type);
    result.put(LearnerMemoryRecallToolContracts.FIELD_STATUS, status);
    result.put(LearnerMemoryRecallToolContracts.FIELD_FAILURE_CODE, code);
    result.put(LearnerMemoryRecallToolContracts.FIELD_MESSAGE, stableMessage(code));
    return result;
  }

  static ObjectNode failureForScope(String type, LearnerMemoryRunScopeRegistry.RecallScopeUseStatus status) {
    return status == LearnerMemoryRunScopeRegistry.RecallScopeUseStatus.BUDGET_EXHAUSTED
        ? failure(type, LearnerMemoryRecallToolContracts.STATUS_BUDGET_EXHAUSTED,
            LearnerMemoryRecallToolContracts.FAILURE_BUDGET_EXHAUSTED)
        : failure(type, LearnerMemoryRecallToolContracts.STATUS_FAILED,
            LearnerMemoryRecallToolContracts.FAILURE_SCOPE_UNAVAILABLE);
  }

  static JsonNode complete(LearnerMemoryRunScopeRegistry.RecallScopeUse use, ObjectNode result) {
    use.complete(result.toString().length());
    return result;
  }

  static boolean fits(ObjectNode result, int maxVisibleChars) {
    return result.toString().length() <= maxVisibleChars;
  }

  static int requiredLimit(JsonNode arguments, Set<String> allowedFields) {
    if (!hasOnlyFields(arguments, allowedFields)) {
      return -1;
    }
    JsonNode value = arguments.get(LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT);
    if (value == null || !value.canConvertToInt()) {
      return -1;
    }
    int limit = value.intValue();
    return limit >= 1 && limit <= LearnerMemoryRecallToolContracts.MAX_ITEMS ? limit : -1;
  }

  static String requiredText(JsonNode arguments, String fieldName) {
    JsonNode value = arguments == null ? null : arguments.get(fieldName);
    return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
  }

  static String optionalText(JsonNode arguments, String fieldName) {
    JsonNode value = arguments == null ? null : arguments.get(fieldName);
    return value == null || value.isNull() ? "" : value.isTextual() ? value.asText().trim() : null;
  }

  static Set<Long> optionalPositiveLongs(JsonNode arguments, String fieldName) {
    JsonNode values = arguments == null ? null : arguments.get(fieldName);
    if (values == null || values.isNull()) {
      return Set.of();
    }
    if (!values.isArray() || values.size() > LearnerMemoryRecallToolContracts.MAX_ITEMS) {
      return null;
    }
    Set<Long> result = new LinkedHashSet<>();
    for (JsonNode value : values) {
      if (!value.canConvertToLong() || value.longValue() < 1 || !result.add(value.longValue())) {
        return null;
      }
    }
    return Set.copyOf(result);
  }

  static LearnerMemoryRecallSnapshot.Section section(
      LearnerMemoryRecallSnapshot snapshot, String sectionRef) {
    if (snapshot == null || sectionRef == null || sectionRef.isBlank()) {
      return null;
    }
    return snapshot.sections().stream()
        .filter(section -> sectionRef.equals(section.sectionRef()))
        .findFirst()
        .orElse(null);
  }

  static LearnerMemoryRecallSnapshot.Statement statement(
      LearnerMemoryRecallSnapshot snapshot, String statementRef) {
    if (snapshot == null || statementRef == null || statementRef.isBlank()) {
      return null;
    }
    return snapshot.sections().stream()
        .flatMap(section -> section.statements().stream())
        .filter(statement -> statementRef.equals(statement.statementRef()))
        .findFirst()
        .orElse(null);
  }

  static void writeStatement(
      ObjectNode target,
      LearnerMemoryRecallSnapshot.Section section,
      LearnerMemoryRecallSnapshot.Statement statement
  ) {
    target.put(LearnerMemoryRecallToolContracts.FIELD_SECTION_REF, section.sectionRef());
    target.put(LearnerMemoryRecallToolContracts.FIELD_SECTION_TITLE, section.title());
    target.put(LearnerMemoryRecallToolContracts.FIELD_STATEMENT_REF, statement.statementRef());
    target.put(LearnerMemoryRecallToolContracts.FIELD_CLAIM_TEXT, statement.claim().claimText());
    target.put(LearnerMemoryRecallToolContracts.FIELD_SOURCE_SUMMARY, statement.sourceSummary());
    target.put(LearnerMemoryRecallToolContracts.FIELD_CURRENT_PROBLEM_MATCH, statement.currentProblemMatch());
  }

  static String normalizedText(String value) {
    return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  static OptionalInt cursorOffset(
      LearnerMemoryRunScopeRegistry.RecallScopeUse use,
      String cursor,
      String type,
      String subject
  ) {
    if (cursor == null || cursor.isBlank()) {
      return OptionalInt.of(0);
    }
    return use.resolveCursor(cursor, type, subject);
  }

  static ArrayNode items(ObjectNode result) {
    return result.putArray(LearnerMemoryRecallToolContracts.FIELD_ITEMS);
  }

  private static boolean hasOnlyFields(JsonNode arguments, Set<String> allowedFields) {
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

  private static String stableMessage(String code) {
    return switch (code) {
      case LearnerMemoryRecallToolContracts.FAILURE_BUDGET_EXHAUSTED -> "Learner memory tool budget is exhausted.";
      case LearnerMemoryRecallToolContracts.FAILURE_SCOPE_UNAVAILABLE,
          LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE ->
          "Requested learner memory is not available in the current run.";
      case LearnerMemoryRecallToolContracts.FAILURE_INVALID_ARGUMENTS -> "Tool arguments are invalid.";
      default -> "Learner memory lookup is unavailable.";
    };
  }
}
