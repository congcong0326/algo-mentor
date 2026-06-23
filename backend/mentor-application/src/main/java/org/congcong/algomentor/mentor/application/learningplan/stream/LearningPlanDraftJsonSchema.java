package org.congcong.algomentor.mentor.application.learningplan.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 学习计划草案 provider-native structured output JSON Schema。
 */
public final class LearningPlanDraftJsonSchema {

  private LearningPlanDraftJsonSchema() {
  }

  public static JsonNode schema() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("title", string());
    properties.set("summary", string());
    properties.set("intent", enumString("PRACTICE_GOAL", "ABILITY_DIAGNOSIS", "INTERVIEW_SPRINT",
        "TOPIC_BREAKTHROUGH", "MISTAKE_REVIEW", "LONG_TERM_LEARNING"));
    properties.set("goal", string());
    properties.set("durationWeeks", integer(1, 52));
    properties.set("level", enumString("BEGINNER", "INTERMEDIATE", "ADVANCED"));
    properties.set("weeklyHours", integer(1, 80));
    properties.set("programmingLanguage", nullableString());
    properties.set("difficultyPreference", nullableEnumString("EASY", "MEDIUM", "HARD", "MIXED"));
    properties.set("interviewOriented", bool());
    properties.set("topicPreferences", stringArray());
    properties.set("profileSummary", string());
    properties.set("phases", phases());
    properties.set("metadata", metadata());
    require(root, "title", "summary", "intent", "goal", "durationWeeks", "level", "weeklyHours",
        "programmingLanguage", "difficultyPreference", "interviewOriented", "topicPreferences",
        "profileSummary", "phases", "metadata");
    return root;
  }

  private static JsonNode phases() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "array");
    schema.put("minItems", 1);
    schema.put("maxItems", 5);
    schema.set("items", phase());
    return schema;
  }

  private static JsonNode phase() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("phaseIndex", integer(1, 10));
    properties.set("title", string());
    properties.set("durationWeeks", integer(1, 52));
    properties.set("focus", string());
    properties.set("objectives", stringArray());
    properties.set("recommendedTags", stringArray());
    properties.set("acceptanceCriteria", stringArray());
    properties.set("reviewAdvice", string());
    properties.set("problems", problems());
    require(root, "phaseIndex", "title", "durationWeeks", "focus", "objectives", "recommendedTags",
        "acceptanceCriteria", "reviewAdvice", "problems");
    return root;
  }

  private static JsonNode problems() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "array");
    schema.put("maxItems", 5);
    schema.set("items", problem());
    return schema;
  }

  private static JsonNode problem() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("slug", string());
    properties.set("frontendId", nullableInteger());
    properties.set("title", string());
    properties.set("titleCn", nullableString());
    properties.set("difficulty", nullableEnumString("EASY", "MEDIUM", "HARD"));
    properties.set("tags", stringArray());
    properties.set("reason", string());
    properties.set("sortOrder", integer(1, 5));
    require(root, "slug", "frontendId", "title", "titleCn", "difficulty", "tags", "reason", "sortOrder");
    return root;
  }

  private static JsonNode metadata() {
    ObjectNode schema = object();
    schema.put("additionalProperties", false);
    ObjectNode properties = schema.putObject("properties");
    properties.set("problemRecommendationIncomplete", bool());
    require(schema, "problemRecommendationIncomplete");
    return schema;
  }

  private static ObjectNode object() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "object");
    return node;
  }

  private static ObjectNode string() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "string");
    return node;
  }

  private static ObjectNode nullableString() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    ArrayNode type = node.putArray("type");
    type.add("string");
    type.add("null");
    return node;
  }

  private static ObjectNode bool() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "boolean");
    return node;
  }

  private static ObjectNode integer(int minimum, int maximum) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "integer");
    node.put("minimum", minimum);
    node.put("maximum", maximum);
    return node;
  }

  private static ObjectNode nullableInteger() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    ArrayNode type = node.putArray("type");
    type.add("integer");
    type.add("null");
    return node;
  }

  private static ObjectNode stringArray() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "array");
    node.set("items", string());
    return node;
  }

  private static ObjectNode enumString(String... values) {
    ObjectNode node = string();
    ArrayNode enums = node.putArray("enum");
    for (String value : values) {
      enums.add(value);
    }
    return node;
  }

  private static ObjectNode nullableEnumString(String... values) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    ArrayNode type = node.putArray("type");
    type.add("string");
    type.add("null");
    ArrayNode enums = node.putArray("enum");
    for (String value : values) {
      enums.add(value);
    }
    enums.addNull();
    return node;
  }

  private static void require(ObjectNode node, String... fields) {
    ArrayNode required = node.putArray("required");
    for (String field : fields) {
      required.add(field);
    }
  }
}
