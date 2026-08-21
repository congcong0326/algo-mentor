package org.congcong.algomentor.mentor.application.profile.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Comparator;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;

/** 用户自述画像模型决策的 provider-native 严格 JSON Schema。 */
public final class DeclaredProfileUpdateJsonSchema {

  public static final String SCHEMA_NAME = "learner_declared_profile_update_v3";
  public static final String OPERATIONS = "operations";
  public static final String OPERATION_ACTION = "action";
  public static final String OPERATION_DIMENSION = "dimension";
  public static final String OPERATION_TARGET_REVISION_ID = "targetRevisionId";
  public static final String OPERATION_CLAIM_TEXT = "claimText";

  private DeclaredProfileUpdateJsonSchema() {
  }

  /** 根据服务端受信候选生成本次调用专属 Schema，只允许对应维度、意图和 active revisionId 的操作。 */
  public static JsonNode schema(List<DeclaredProfileUpdateAgentInput.Candidate> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      throw new IllegalArgumentException("Declared profile schema candidates must not be empty");
    }
    List<DeclaredProfileUpdateAgentInput.Candidate> values = List.copyOf(candidates);
    List<String> declareDimensions = values.stream()
        .filter(candidate -> candidate.intent() == DeclaredProfileUpdateIntent.DECLARE)
        .map(candidate -> candidate.dimension().name())
        .sorted()
        .toList();
    List<Long> correctionTargets = values.stream()
        .filter(candidate -> candidate.intent() == DeclaredProfileUpdateIntent.CORRECT)
        .flatMap(candidate -> candidate.activeClaims().stream())
        .map(DeclaredProfileUpdateAgentInput.ActiveClaim::revisionId)
        .distinct()
        .sorted(Comparator.naturalOrder())
        .toList();
    ObjectNode root = object();
    root.put("additionalProperties", false);
    root.putObject("properties").set(OPERATIONS, operations(values.size(), declareDimensions, correctionTargets));
    require(root, OPERATIONS);
    return root;
  }

  private static ObjectNode operations(
      int candidateCount,
      List<String> declareDimensions,
      List<Long> correctionTargets) {
    ObjectNode array = object();
    array.put("type", "array");
    ArrayNode variants = JsonNodeFactory.instance.arrayNode();
    if (!declareDimensions.isEmpty()) {
      variants.add(add(declareDimensions));
    }
    if (!correctionTargets.isEmpty()) {
      variants.add(revise(correctionTargets));
      variants.add(retire(correctionTargets));
    }
    array.put("maxItems", variants.isEmpty() ? 0 : candidateCount);
    if (!variants.isEmpty()) {
      array.putObject("items").set("anyOf", variants);
    }
    return array;
  }

  private static ObjectNode add(List<String> dimensions) {
    ObjectNode root = object();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(OPERATION_ACTION, stringEnum(java.util.List.of("ADD")));
    properties.set(OPERATION_DIMENSION, stringEnum(dimensions));
    properties.set(OPERATION_CLAIM_TEXT, string());
    require(root, OPERATION_ACTION, OPERATION_DIMENSION, OPERATION_CLAIM_TEXT);
    return root;
  }

  private static ObjectNode revise(List<Long> targetRevisionIds) {
    ObjectNode root = object();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(OPERATION_ACTION, stringEnum(java.util.List.of("REVISE")));
    properties.set(OPERATION_TARGET_REVISION_ID, positiveIntegerEnum(targetRevisionIds));
    properties.set(OPERATION_CLAIM_TEXT, string());
    require(root, OPERATION_ACTION, OPERATION_TARGET_REVISION_ID, OPERATION_CLAIM_TEXT);
    return root;
  }

  private static ObjectNode retire(List<Long> targetRevisionIds) {
    ObjectNode root = object();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(OPERATION_ACTION, stringEnum(java.util.List.of("RETIRE")));
    properties.set(OPERATION_TARGET_REVISION_ID, positiveIntegerEnum(targetRevisionIds));
    require(root, OPERATION_ACTION, OPERATION_TARGET_REVISION_ID);
    return root;
  }

  private static ObjectNode string() {
    ObjectNode value = object();
    value.put("type", "string");
    return value;
  }

  private static ObjectNode stringEnum(java.util.List<String> values) {
    ObjectNode value = string();
    ArrayNode enumeration = value.putArray("enum");
    values.forEach(enumeration::add);
    return value;
  }

  private static ObjectNode positiveIntegerEnum(List<Long> values) {
    ObjectNode value = object();
    value.put("type", "integer");
    value.put("minimum", 1);
    ArrayNode enumeration = value.putArray("enum");
    values.forEach(enumeration::add);
    return value;
  }

  private static ObjectNode object() {
    // OpenAI 严格 JSON Schema 要求根对象也显式声明 type。
    return JsonNodeFactory.instance.objectNode().put("type", "object");
  }

  private static void require(ObjectNode root, String... names) {
    ArrayNode required = root.putArray("required");
    for (String name : names) {
      required.add(name);
    }
  }
}
