package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistory;

/** 三项只读 Review 工具的严格参数、稳定失败和受限结果辅助方法。 */
final class LearnerMemoryReviewToolSupport {

  private static final Set<String> PROTECTED_TEXT_FIELDS = Set.of(
      LearnerMemoryAgentToolContracts.RESULT_FIELD_TYPE,
      LearnerMemoryAgentToolContracts.RESULT_FIELD_STATUS,
      LearnerMemoryAgentToolContracts.RESULT_FIELD_FAILURE_CODE,
      LearnerMemoryAgentToolContracts.RESULT_FIELD_MESSAGE);

  private LearnerMemoryReviewToolSupport() {
  }

  static ObjectNode success(String type) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_TYPE, type);
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_STATUS, LearnerMemoryAgentToolContracts.STATUS_OK);
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_TRUNCATED, false);
    return result;
  }

  static ObjectNode failure(String type, String status, String failureCode) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_TYPE, type);
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_STATUS, status);
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_FAILURE_CODE, failureCode);
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_MESSAGE, stableMessage(failureCode));
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_TRUNCATED, false);
    return result;
  }

  static ObjectNode failureForScopeUse(String type, LearnerMemoryRunScopeRegistry.ScopeUseStatus status) {
    return switch (status) {
      case BUDGET_EXHAUSTED -> failure(
          type,
          LearnerMemoryAgentToolContracts.STATUS_BUDGET_EXHAUSTED,
          LearnerMemoryAgentToolContracts.FAILURE_BUDGET_EXHAUSTED);
      case ALREADY_USED -> failure(
          type,
          LearnerMemoryAgentToolContracts.STATUS_FAILED,
          LearnerMemoryAgentToolContracts.FAILURE_TOOL_ALREADY_USED);
      case FORBIDDEN -> failure(
          type,
          LearnerMemoryAgentToolContracts.STATUS_FAILED,
          LearnerMemoryAgentToolContracts.FAILURE_SCOPE_FORBIDDEN);
      case SCOPE_UNAVAILABLE -> failure(
          type,
          LearnerMemoryAgentToolContracts.STATUS_FAILED,
          LearnerMemoryAgentToolContracts.FAILURE_SCOPE_UNAVAILABLE);
      case GRANTED -> throw new IllegalArgumentException("Granted scope use is not a failure");
    };
  }

  static String scopeRef(AgentExecutionContext context) {
    if (context == null) {
      return "";
    }
    Object value = context.requestMetadata().get(LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF);
    return value == null ? "" : value.toString().trim();
  }

  static String requiredStringArgument(JsonNode arguments, String fieldName) {
    if (!hasOnlyFields(arguments, Set.of(fieldName))) {
      return null;
    }
    JsonNode value = arguments.get(fieldName);
    return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
  }

  static Long requiredPositiveLongArgument(JsonNode arguments, String fieldName) {
    if (!hasOnlyFields(arguments, Set.of(fieldName))) {
      return null;
    }
    JsonNode value = arguments.get(fieldName);
    return value != null && value.canConvertToLong() && value.longValue() > 0 ? value.longValue() : null;
  }

  static long[] requiredOrderedPositiveLongArguments(JsonNode arguments, String firstField, String secondField) {
    if (!hasOnlyFields(arguments, Set.of(firstField, secondField))) {
      return null;
    }
    JsonNode first = arguments.get(firstField);
    JsonNode second = arguments.get(secondField);
    if (first == null || second == null || !first.canConvertToLong() || !second.canConvertToLong()
        || first.longValue() < 1 || second.longValue() < 1) {
      return null;
    }
    return new long[] {first.longValue(), second.longValue()};
  }

  static ObjectNode inputSchema(String fieldName, String fieldType) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ObjectNode property = root.putObject("properties").putObject(fieldName);
    property.put("type", fieldType);
    root.putArray("required").add(fieldName);
    return root;
  }

  static ObjectNode twoIntegerArgumentsSchema(String firstField, String secondField) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.putObject(firstField).put("type", "integer");
    properties.putObject(secondField).put("type", "integer");
    root.putArray("required").add(firstField).add(secondField);
    return root;
  }

  static void writeReview(ObjectNode target, CodeReviewHistory review) {
    target.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_REVIEW_ID, review.reviewId());
    target.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_PROBLEM_SLUG, review.problemSlug());
    target.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_VERSION_NO, review.versionNo());
    target.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_CREATED_AT, review.createdAt().toString());
    target.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_PASSED, review.passed());
    writeScore(target.putObject(LearnerMemoryAgentToolContracts.RESULT_FIELD_SCORE), review.score());
    writeStrings(target.putArray(LearnerMemoryAgentToolContracts.RESULT_FIELD_DEDUCTION_REASONS), review.deductionReasons());
    writeStrings(target.putArray(LearnerMemoryAgentToolContracts.RESULT_FIELD_IMPROVEMENT_SUGGESTIONS),
        review.improvementSuggestions());
    review.affectedTagIds().forEach(target.putArray(LearnerMemoryAgentToolContracts.RESULT_FIELD_AFFECTED_TAG_IDS)::add);
  }

  static void writeScore(ObjectNode target, PracticeCodeReviewScore score) {
    writeDecimal(target, LearnerMemoryAgentToolContracts.RESULT_FIELD_SCORE_CORRECTNESS, score.correctness());
    writeDecimal(target, LearnerMemoryAgentToolContracts.RESULT_FIELD_SCORE_COMPLEXITY, score.complexity());
    writeDecimal(target, LearnerMemoryAgentToolContracts.RESULT_FIELD_SCORE_EDGE_CASES, score.edgeCases());
    writeDecimal(target, LearnerMemoryAgentToolContracts.RESULT_FIELD_SCORE_CODE_QUALITY, score.codeQuality());
    writeDecimal(target, LearnerMemoryAgentToolContracts.RESULT_FIELD_SCORE_PROBLEM_FIT, score.problemFit());
    writeDecimal(target, LearnerMemoryAgentToolContracts.RESULT_FIELD_SCORE_TOTAL, score.total());
  }

  static void writeStrings(ArrayNode target, List<String> values) {
    if (values != null) {
      values.forEach(target::add);
    }
  }

  static JsonNode limitResult(ObjectNode result) {
    if (result.toString().length() <= LearnerMemoryAgentToolContracts.MAX_TOOL_RESULT_CHARS) {
      return result;
    }
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_TRUNCATED, true);
    while (result.toString().length() > LearnerMemoryAgentToolContracts.MAX_TOOL_RESULT_CHARS) {
      TextLocation location = longestMutableText(result);
      if (location != null) {
        String value = location.value();
        int overage = result.toString().length() - LearnerMemoryAgentToolContracts.MAX_TOOL_RESULT_CHARS;
        int retained = Math.max(0, value.length() - overage - 3);
        location.replace(retained == 0 ? "" : value.substring(0, retained) + "...");
        continue;
      }
      ArrayNode array = largestArray(result);
      if (array != null && !array.isEmpty()) {
        array.remove(array.size() - 1);
        continue;
      }
      break;
    }
    return result;
  }

  private static boolean hasOnlyFields(JsonNode arguments, Set<String> expected) {
    if (arguments == null || !arguments.isObject() || arguments.size() != expected.size()) {
      return false;
    }
    Iterator<String> fields = arguments.fieldNames();
    while (fields.hasNext()) {
      if (!expected.contains(fields.next())) {
        return false;
      }
    }
    return true;
  }

  private static String stableMessage(String failureCode) {
    return switch (failureCode) {
      case LearnerMemoryAgentToolContracts.FAILURE_BUDGET_EXHAUSTED -> "Review memory tool budget is exhausted.";
      case LearnerMemoryAgentToolContracts.FAILURE_SCOPE_UNAVAILABLE -> "Review memory scope is unavailable.";
      case LearnerMemoryAgentToolContracts.FAILURE_SCOPE_FORBIDDEN -> "Requested Review data is outside the current scope.";
      case LearnerMemoryAgentToolContracts.FAILURE_TOOL_ALREADY_USED -> "This Review memory lookup has already been used.";
      case LearnerMemoryAgentToolContracts.FAILURE_INVALID_ARGUMENTS -> "Tool arguments are invalid.";
      case LearnerMemoryAgentToolContracts.FAILURE_REVIEW_NOT_FOUND -> "Requested Review data is unavailable.";
      case LearnerMemoryAgentToolContracts.FAILURE_REVIEW_DATA_UNAVAILABLE -> "Review detail is unavailable.";
      default -> "Review memory tool could not complete.";
    };
  }

  private static void writeDecimal(ObjectNode target, String field, BigDecimal value) {
    target.put(field, value);
  }

  private static TextLocation longestMutableText(JsonNode node) {
    List<TextLocation> locations = new ArrayList<>();
    collectTextLocations(node, locations);
    return locations.stream().filter(location -> !location.value().isEmpty())
        .max(java.util.Comparator.comparingInt(location -> location.value().length())).orElse(null);
  }

  private static void collectTextLocations(JsonNode node, List<TextLocation> locations) {
    if (node instanceof ObjectNode object) {
      object.fields().forEachRemaining(entry -> {
        if (entry.getValue() instanceof TextNode text && !PROTECTED_TEXT_FIELDS.contains(entry.getKey())) {
          locations.add(new ObjectTextLocation(object, entry.getKey(), text.textValue()));
        } else {
          collectTextLocations(entry.getValue(), locations);
        }
      });
    } else if (node instanceof ArrayNode array) {
      for (int index = 0; index < array.size(); index++) {
        JsonNode item = array.get(index);
        if (item instanceof TextNode text) {
          locations.add(new ArrayTextLocation(array, index, text.textValue()));
        } else {
          collectTextLocations(item, locations);
        }
      }
    }
  }

  private static ArrayNode largestArray(JsonNode node) {
    List<ArrayNode> arrays = new ArrayList<>();
    collectArrays(node, arrays);
    return arrays.stream().filter(array -> !array.isEmpty())
        .max(java.util.Comparator.comparingInt(ArrayNode::size)).orElse(null);
  }

  private static void collectArrays(JsonNode node, List<ArrayNode> arrays) {
    if (node instanceof ObjectNode object) {
      object.elements().forEachRemaining(value -> collectArrays(value, arrays));
    } else if (node instanceof ArrayNode array) {
      arrays.add(array);
      array.elements().forEachRemaining(value -> collectArrays(value, arrays));
    }
  }

  private sealed interface TextLocation permits ObjectTextLocation, ArrayTextLocation {

    String value();

    void replace(String value);
  }

  private record ObjectTextLocation(ObjectNode parent, String field, String value) implements TextLocation {

    @Override
    public void replace(String replacement) {
      parent.put(field, replacement);
    }
  }

  private record ArrayTextLocation(ArrayNode parent, int index, String value) implements TextLocation {

    @Override
    public void replace(String replacement) {
      parent.set(index, TextNode.valueOf(replacement));
    }
  }
}
