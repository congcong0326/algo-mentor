package org.congcong.algomentor.mentor.application.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 复述判定 provider-native structured output JSON Schema。
 */
public final class RecallJudgeJsonSchema {

  private RecallJudgeJsonSchema() {
  }

  public static JsonNode schema() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("suggestedRating", stringEnum("AGAIN", "HARD", "GOOD", "EASY"));
    properties.set("hitPoints", stringArray());
    properties.set("missedPoints", stringArray());
    properties.set("gapSummary", string());
    require(root, "suggestedRating", "hitPoints", "missedPoints", "gapSummary");
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

  private static ObjectNode stringEnum(String... values) {
    ObjectNode node = string();
    ArrayNode enumValues = node.putArray("enum");
    for (String value : values) {
      enumValues.add(value);
    }
    return node;
  }

  private static ObjectNode stringArray() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "array");
    node.set("items", string());
    return node;
  }

  private static void require(ObjectNode node, String... fields) {
    ArrayNode required = node.putArray("required");
    for (String field : fields) {
      required.add(field);
    }
  }
}
