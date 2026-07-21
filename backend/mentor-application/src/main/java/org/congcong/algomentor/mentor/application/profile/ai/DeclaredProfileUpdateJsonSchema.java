package org.congcong.algomentor.mentor.application.profile.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileContract;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateAction;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;

/** 用户自述画像模型决策的 provider-native 严格 JSON Schema。 */
public final class DeclaredProfileUpdateJsonSchema {

  public static final String SCHEMA_NAME = "learner_declared_profile_update_v1";
  public static final String DECISIONS = "decisions";
  public static final String DECISION_DIMENSION = "dimension";
  public static final String DECISION_ACTION = "action";
  public static final String DECISION_CONTENT = "content";

  private DeclaredProfileUpdateJsonSchema() {
  }

  public static JsonNode schema() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    root.putObject("properties").set(DECISIONS, decisions());
    require(root, DECISIONS);
    return root;
  }

  private static ObjectNode decisions() {
    ObjectNode array = object();
    array.put("type", "array");
    array.set("items", decision());
    return array;
  }

  private static ObjectNode decision() {
    ObjectNode root = object();
    root.put("type", "object");
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(DECISION_DIMENSION, stringEnum(LearnerProfileContract.declaredDimensions().stream()
        .map(Enum::name).sorted().toList()));
    properties.set(DECISION_ACTION, stringEnum(java.util.List.of(
        ProfileUpdateAction.NO_CHANGE.name(), ProfileUpdateAction.REPLACE.name())));
    properties.set(DECISION_CONTENT, string());
    require(root, DECISION_DIMENSION, DECISION_ACTION, DECISION_CONTENT);
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
