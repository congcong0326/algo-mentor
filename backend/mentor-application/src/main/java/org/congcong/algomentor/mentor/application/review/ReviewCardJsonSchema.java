package org.congcong.algomentor.mentor.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 复习卡 provider-native structured output JSON Schema。
 */
public final class ReviewCardJsonSchema {

  private ReviewCardJsonSchema() {
  }

  public static JsonNode schema() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("cardVariant", stringEnum("AI_GENERATED"));
    properties.set("problemRef", problemRef());
    properties.set("contextSummary", string());
    properties.set("prompts", promptArray());
    properties.set("scaffold", scaffold());
    properties.set("revealPolicy", stringEnum(MistakeReviewConstants.REVEAL_HIDE_PREVIOUS));
    properties.set("expectedEffort", stringEnum("LIGHT", "MEDIUM", "HEAVY"));
    require(root, "cardVariant", "problemRef", "contextSummary", "prompts", "scaffold", "revealPolicy",
        "expectedEffort");
    return root;
  }

  private static JsonNode problemRef() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("slug", string());
    properties.set("titleCn", string());
    properties.set("difficulty", string());
    require(root, "slug", "titleCn", "difficulty");
    return root;
  }

  private static JsonNode promptArray() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "array");
    node.put("minItems", 1);
    node.set("items", prompt());
    return node;
  }

  private static JsonNode prompt() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("key", string());
    properties.set("label", string());
    properties.set("hint", string());
    require(root, "key", "label", "hint");
    return root;
  }

  private static JsonNode scaffold() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("templateMarkdown", string());
    properties.set("maxInputChars", integer(1, 2000));
    require(root, "templateMarkdown", "maxInputChars");
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

  private static ObjectNode integer(int minimum, int maximum) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "integer");
    node.put("minimum", minimum);
    node.put("maximum", maximum);
    return node;
  }

  private static ObjectNode stringEnum(String... values) {
    ObjectNode node = string();
    ArrayNode enumValues = node.putArray("enum");
    for (String value : values) {
      enumValues.add(value);
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
