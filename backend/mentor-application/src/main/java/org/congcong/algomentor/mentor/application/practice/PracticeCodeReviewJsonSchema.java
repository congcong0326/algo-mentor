package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;

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
    properties.set(PracticeCodeReviewConstants.JSON_JUDGE_ASSESSMENT, judgeAssessment());
    properties.set(PracticeCodeReviewConstants.JSON_SCORE_EXPLANATIONS, scoreExplanations());
    properties.set("scores", scores());
    properties.set("passed", bool());
    properties.set("deductionReasons", stringArray());
    properties.set("improvementSuggestions", stringArray());
    properties.set("reviewMarkdown", string());
    properties.set(PracticeCodeReviewConstants.JSON_AFFECTED_TAG_IDS, positiveIntegerArray());
    properties.set(
        PracticeCodeReviewConstants.JSON_REVIEW_HISTORY_SUMMARY,
        string(PracticeCodeReviewConstants.REVIEW_HISTORY_SUMMARY_MAX_LENGTH));
    require(root, "isCodeSubmission", "belongsToCurrentProblem", "isCompleteLeetCodeSolution", "language",
        "rawCode", "normalizedCode", "evidence", "contextSummary",
        PracticeCodeReviewConstants.JSON_JUDGE_ASSESSMENT,
        PracticeCodeReviewConstants.JSON_SCORE_EXPLANATIONS,
        "scores", "passed", "deductionReasons", "improvementSuggestions", "reviewMarkdown",
        PracticeCodeReviewConstants.JSON_AFFECTED_TAG_IDS,
        PracticeCodeReviewConstants.JSON_REVIEW_HISTORY_SUMMARY);
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
    properties.set("correctness", scoreLevel(PracticeCodeReviewConstants.CORRECTNESS_SCORE_LEVELS, 0, 4));
    properties.set("complexity", scoreLevel(PracticeCodeReviewConstants.COMPLEXITY_SCORE_LEVELS, 0, 2));
    properties.set("edgeCases", scoreLevel(PracticeCodeReviewConstants.EDGE_CASE_SCORE_LEVELS, 0, 2));
    properties.set("codeQuality", scoreLevel(PracticeCodeReviewConstants.CODE_QUALITY_SCORE_LEVELS, 0, 1));
    properties.set("problemFit", scoreLevel(PracticeCodeReviewConstants.PROBLEM_FIT_SCORE_LEVELS, 0, 1));
    properties.set("total", number(0, 10));
    require(root, "correctness", "complexity", "edgeCases", "codeQuality", "problemFit", "total");
    return root;
  }

  private static JsonNode judgeAssessment() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(
        PracticeCodeReviewConstants.JSON_JUDGE_VERDICT,
        enumString(PracticeCodeReviewJudgeVerdict.values()));
    properties.set(
        PracticeCodeReviewConstants.JSON_VERDICT_BASIS,
        enumString(PracticeCodeReviewVerdictBasis.values()));
    properties.set(PracticeCodeReviewConstants.JSON_BLOCKING_ISSUE, bool());
    properties.set(PracticeCodeReviewConstants.JSON_MEETS_EXPECTED_COMPLEXITY, bool());
    properties.set(PracticeCodeReviewConstants.JSON_TIME_COMPLEXITY, string());
    properties.set(PracticeCodeReviewConstants.JSON_SPACE_COMPLEXITY, string());
    properties.set(PracticeCodeReviewConstants.JSON_EXPECTED_TIME_COMPLEXITY, string());
    properties.set(PracticeCodeReviewConstants.JSON_CONSTRAINT_ANALYSIS, string());
    require(
        root,
        PracticeCodeReviewConstants.JSON_JUDGE_VERDICT,
        PracticeCodeReviewConstants.JSON_VERDICT_BASIS,
        PracticeCodeReviewConstants.JSON_BLOCKING_ISSUE,
        PracticeCodeReviewConstants.JSON_MEETS_EXPECTED_COMPLEXITY,
        PracticeCodeReviewConstants.JSON_TIME_COMPLEXITY,
        PracticeCodeReviewConstants.JSON_SPACE_COMPLEXITY,
        PracticeCodeReviewConstants.JSON_EXPECTED_TIME_COMPLEXITY,
        PracticeCodeReviewConstants.JSON_CONSTRAINT_ANALYSIS);
    return root;
  }

  private static JsonNode scoreExplanations() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(PracticeCodeReviewConstants.JSON_SCORE_CORRECTNESS, string());
    properties.set(PracticeCodeReviewConstants.JSON_SCORE_COMPLEXITY, string());
    properties.set(PracticeCodeReviewConstants.JSON_SCORE_EDGE_CASES, string());
    properties.set(PracticeCodeReviewConstants.JSON_SCORE_CODE_QUALITY, string());
    properties.set(PracticeCodeReviewConstants.JSON_SCORE_PROBLEM_FIT, string());
    require(
        root,
        PracticeCodeReviewConstants.JSON_SCORE_CORRECTNESS,
        PracticeCodeReviewConstants.JSON_SCORE_COMPLEXITY,
        PracticeCodeReviewConstants.JSON_SCORE_EDGE_CASES,
        PracticeCodeReviewConstants.JSON_SCORE_CODE_QUALITY,
        PracticeCodeReviewConstants.JSON_SCORE_PROBLEM_FIT);
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

  private static ObjectNode string(int maxLength) {
    ObjectNode node = string();
    node.put("maxLength", maxLength);
    return node;
  }

  private static ObjectNode enumString(Enum<?>[] values) {
    ObjectNode node = string();
    ArrayNode allowedValues = node.putArray("enum");
    for (Enum<?> value : values) {
      allowedValues.add(value.name());
    }
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

  private static ObjectNode scoreLevel(List<BigDecimal> levels, int minimum, int maximum) {
    ObjectNode node = number(minimum, maximum);
    ArrayNode allowedValues = node.putArray("enum");
    levels.forEach(allowedValues::add);
    return node;
  }

  private static ObjectNode stringArray() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "array");
    node.set("items", string());
    return node;
  }

  private static ObjectNode positiveIntegerArray() {
    ObjectNode node = JsonNodeFactory.instance.objectNode();
    node.put("type", "array");
    ObjectNode item = node.putObject("items");
    item.put("type", "integer");
    item.put("minimum", 1);
    return node;
  }

  private static void require(ObjectNode node, String... fields) {
    ArrayNode required = node.putArray("required");
    for (String field : fields) {
      required.add(field);
    }
  }
}
