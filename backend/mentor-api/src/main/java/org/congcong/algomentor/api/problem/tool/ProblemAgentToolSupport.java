package org.congcong.algomentor.api.problem.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;

final class ProblemAgentToolSupport {

  static final String ENUM = "enum";
  static final String PROPERTIES = "properties";
  static final String REQUIRED = "required";

  private static final String TYPE = "type";
  private static final String OBJECT = "object";
  private static final String STRING = "string";
  private static final String BOOLEAN = "boolean";
  private static final String INTEGER = "integer";
  private static final String NULL = "null";
  private static final String DESCRIPTION = "description";
  private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
  private static final String MINIMUM = "minimum";
  private static final String MAXIMUM = "maximum";

  private ProblemAgentToolSupport() {
  }

  static ObjectNode objectSchema() {
    return JsonNodeFactory.instance.objectNode()
        .put(TYPE, OBJECT)
        .put(ADDITIONAL_PROPERTIES, false);
  }

  static ObjectNode stringProperty(String description) {
    return JsonNodeFactory.instance.objectNode()
        .put(TYPE, STRING)
        .put(DESCRIPTION, description);
  }

  static ObjectNode nullableStringProperty(String description) {
    return nullableProperty(STRING, description);
  }

  static ObjectNode booleanProperty(String description) {
    return JsonNodeFactory.instance.objectNode()
        .put(TYPE, BOOLEAN)
        .put(DESCRIPTION, description);
  }

  static ObjectNode nullableBooleanProperty(String description) {
    return nullableProperty(BOOLEAN, description);
  }

  static ObjectNode integerProperty(String description, int minimum, int maximum) {
    ObjectNode node = JsonNodeFactory.instance.objectNode()
        .put(TYPE, INTEGER)
        .put(DESCRIPTION, description)
        .put(MINIMUM, minimum);
    if (maximum > 0) {
      node.put(MAXIMUM, maximum);
    }
    return node;
  }

  static ObjectNode nullableIntegerProperty(String description, int minimum, int maximum) {
    ObjectNode node = nullableProperty(INTEGER, description)
        .put(MINIMUM, minimum);
    if (maximum > 0) {
      node.put(MAXIMUM, maximum);
    }
    return node;
  }

  static void requireAllProperties(ObjectNode schema) {
    JsonNode properties = schema.path(PROPERTIES);
    ArrayNode required = JsonNodeFactory.instance.arrayNode();
    properties.fieldNames().forEachRemaining(required::add);
    schema.set(REQUIRED, required);
  }

  static void requireObjectIfPresent(JsonNode arguments, String toolName) {
    if (arguments != null && !arguments.isNull() && !arguments.isObject()) {
      throw toolFailure(toolName, "Tool arguments must be a JSON object.", null);
    }
  }

  static String requiredText(JsonNode arguments, String fieldName, String toolName) {
    requireObjectIfPresent(arguments, toolName);
    JsonNode value = arguments == null ? null : arguments.get(fieldName);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      throw toolFailure(toolName, fieldName + " must be a non-blank string.", null);
    }
    return value.asText().trim();
  }

  static String optionalText(JsonNode arguments, String fieldName, String toolName) {
    requireObjectIfPresent(arguments, toolName);
    JsonNode value = arguments == null ? null : arguments.get(fieldName);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isTextual()) {
      throw toolFailure(toolName, fieldName + " must be a string.", null);
    }
    String text = value.asText().trim();
    return text.isBlank() ? null : text;
  }

  static boolean optionalBoolean(JsonNode arguments, String fieldName, boolean defaultValue, String toolName) {
    requireObjectIfPresent(arguments, toolName);
    JsonNode value = arguments == null ? null : arguments.get(fieldName);
    if (value == null || value.isNull()) {
      return defaultValue;
    }
    if (!value.isBoolean()) {
      throw toolFailure(toolName, fieldName + " must be a boolean.", null);
    }
    return value.asBoolean();
  }

  static int optionalInt(JsonNode arguments, String fieldName, int defaultValue, String toolName) {
    requireObjectIfPresent(arguments, toolName);
    JsonNode value = arguments == null ? null : arguments.get(fieldName);
    if (value == null || value.isNull()) {
      return defaultValue;
    }
    if (!value.canConvertToInt()) {
      throw toolFailure(toolName, fieldName + " must be an integer.", null);
    }
    return value.asInt();
  }

  static void putNullable(ObjectNode node, String fieldName, String value) {
    if (value == null) {
      node.putNull(fieldName);
      return;
    }
    node.put(fieldName, value);
  }

  static AgentException toolFailure(String toolName, String message, Throwable cause) {
    return new AgentException(
        AgentErrorCode.TOOL_EXECUTION_FAILED,
        message,
        false,
        Map.of(AgentRuntimeMetadataKeys.TOOL_NAME, toolName),
        cause);
  }

  private static ObjectNode nullableProperty(String type, String description) {
    ObjectNode node = JsonNodeFactory.instance.objectNode()
        .put(DESCRIPTION, description);
    node.putArray(TYPE).add(type).add(NULL);
    return node;
  }
}
