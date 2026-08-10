package org.congcong.algomentor.mentor.application.profile.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;

/** 用户自述画像模型决策的 provider-native 严格 JSON Schema。 */
public final class DeclaredProfileUpdateJsonSchema {

  public static final String SCHEMA_NAME = "learner_declared_profile_update_v2";
  public static final String OPERATIONS = "operations";
  public static final String OPERATION_ACTION = "action";
  public static final String OPERATION_DIMENSION = "dimension";
  public static final String OPERATION_TARGET_REVISION_ID = "targetRevisionId";
  public static final String OPERATION_CLAIM_TEXT = "claimText";

  private DeclaredProfileUpdateJsonSchema() {
  }

  public static JsonNode schema() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    root.putObject("properties").set(OPERATIONS, operations());
    require(root, OPERATIONS);
    return root;
  }

  private static ObjectNode operations() {
    ObjectNode array = object();
    array.put("type", "array");
    array.put("maxItems", 10);
    ArrayNode variants = array.putObject("items").putArray("anyOf");
    variants.add(add());
    variants.add(revise());
    variants.add(retire());
    return array;
  }

  private static ObjectNode add() {
    ObjectNode root = object();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(OPERATION_ACTION, stringEnum(java.util.List.of("ADD")));
    properties.set(OPERATION_DIMENSION, string());
    properties.set(OPERATION_CLAIM_TEXT, string());
    require(root, OPERATION_ACTION, OPERATION_DIMENSION, OPERATION_CLAIM_TEXT);
    return root;
  }

  private static ObjectNode revise() {
    ObjectNode root = object();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(OPERATION_ACTION, stringEnum(java.util.List.of("REVISE")));
    properties.set(OPERATION_TARGET_REVISION_ID, positiveInteger());
    properties.set(OPERATION_CLAIM_TEXT, string());
    require(root, OPERATION_ACTION, OPERATION_TARGET_REVISION_ID, OPERATION_CLAIM_TEXT);
    return root;
  }

  private static ObjectNode retire() {
    ObjectNode root = object();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(OPERATION_ACTION, stringEnum(java.util.List.of("RETIRE")));
    properties.set(OPERATION_TARGET_REVISION_ID, positiveInteger());
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

  private static ObjectNode positiveInteger() {
    ObjectNode value = object();
    value.put("type", "integer");
    value.put("minimum", 1);
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
