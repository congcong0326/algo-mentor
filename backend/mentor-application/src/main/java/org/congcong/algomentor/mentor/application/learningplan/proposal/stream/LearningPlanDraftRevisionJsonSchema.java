package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanGeneratedContentJsonSchema;

/** 草案修订使用的严格输出 Schema：已解析规划输入与模型生成内容。 */
public final class LearningPlanDraftRevisionJsonSchema {

  private LearningPlanDraftRevisionJsonSchema() {
  }

  public static JsonNode schema() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("resolvedBrief", resolvedBrief());
    properties.set("generatedContent", LearningPlanGeneratedContentJsonSchema.schema());
    require(root, "resolvedBrief", "generatedContent");
    return root;
  }

  private static JsonNode resolvedBrief() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("intent", enumString(
        "PRACTICE_GOAL", "ABILITY_DIAGNOSIS", "INTERVIEW_SPRINT", "TOPIC_BREAKTHROUGH",
        "MISTAKE_REVIEW", "LONG_TERM_LEARNING"));
    properties.set("objective", nullableString());
    properties.set("durationWeeks", integer(1, 52));
    properties.set("level", enumString("BEGINNER", "INTERMEDIATE", "ADVANCED"));
    properties.set("weeklyHours", integer(1, 80));
    properties.set("programmingLanguage", nullableString());
    properties.set("difficultyDistribution", difficultyDistribution());
    properties.set("interviewOriented", bool());
    properties.set("topicPreferences", stringArray());
    properties.set("additionalConstraints", nullableString());
    properties.set("personalizationEnabled", bool());
    properties.set("contentLocale", enumString("zh-CN", "en-US"));
    require(root, "intent", "objective", "durationWeeks", "level", "weeklyHours",
        "programmingLanguage", "difficultyDistribution", "interviewOriented", "topicPreferences",
        "additionalConstraints", "personalizationEnabled", "contentLocale");
    return root;
  }

  private static JsonNode difficultyDistribution() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("easyPercent", integer(0, 100));
    properties.set("mediumPercent", integer(0, 100));
    properties.set("hardPercent", integer(0, 100));
    require(root, "easyPercent", "mediumPercent", "hardPercent");
    return root;
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

  private static void require(ObjectNode node, String... fields) {
    ArrayNode required = node.putArray("required");
    for (String field : fields) {
      required.add(field);
    }
  }
}
