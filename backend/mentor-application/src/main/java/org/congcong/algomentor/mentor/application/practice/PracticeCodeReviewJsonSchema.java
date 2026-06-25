package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 练习代码 Review provider-native structured output JSON Schema。
 */
public final class PracticeCodeReviewJsonSchema {

  private PracticeCodeReviewJsonSchema() {
  }

  public static JsonNode schema() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("isCodeSubmission", bool());
    properties.set("belongsToCurrentProblem", bool());
    properties.set("isCompleteLeetCodeSolution", bool());
    properties.set("language", string());
    properties.set("rawCode", string());
    properties.set("normalizedCode", string());
    properties.set("evidence", evidenceArray());
    properties.set("contextSummary", string());
    properties.set("scores", scores());
    properties.set("passed", bool());
    properties.set("deductionReasons", stringArray());
    properties.set("improvementSuggestions", stringArray());
    properties.set("reviewMarkdown", string());
    require(root, "isCodeSubmission", "belongsToCurrentProblem", "isCompleteLeetCodeSolution", "language",
        "rawCode", "normalizedCode", "evidence", "contextSummary", "scores", "passed", "deductionReasons",
        "improvementSuggestions", "reviewMarkdown");
    return root;
  }

  private static JsonNode evidenceArray() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "array");
    node.set("items", evidence());
    return node;
  }

  private static JsonNode evidence() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("type", string());
    properties.set("value", string());
    require(root, "type", "value");
    return root;
  }

  private static JsonNode scores() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("correctness", number(0, 4));
    properties.set("complexity", number(0, 2));
    properties.set("edgeCases", number(0, 2));
    properties.set("codeQuality", number(0, 1));
    properties.set("problemFit", number(0, 1));
    properties.set("total", number(0, 10));
    require(root, "correctness", "complexity", "edgeCases", "codeQuality", "problemFit", "total");
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

  private static ObjectNode bool() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "boolean");
    return node;
  }

  private static ObjectNode number(int minimum, int maximum) {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "number");
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

  private static void require(ObjectNode node, String... fields) {
    ArrayNode required = node.putArray("required");
    for (String field : fields) {
      required.add(field);
    }
  }
}
