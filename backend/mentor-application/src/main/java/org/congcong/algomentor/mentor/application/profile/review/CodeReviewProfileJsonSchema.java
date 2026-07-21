package org.congcong.algomentor.mentor.application.profile.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateAction;

/** Code Review 批量画像更新的严格 provider-native JSON Schema。 */
public final class CodeReviewProfileJsonSchema {

  public static final String SCHEMA_NAME = "learner_profile_code_review_batch_v1";
  public static final String GENERAL_OBSERVATIONS = "generalObservations";
  public static final String TAG_ASSESSMENTS = "tagAssessments";
  public static final String DIMENSION = "dimension";
  public static final String TAG_ID = "tagId";
  public static final String ACTION = "action";
  public static final String CONTENT = "content";
  public static final String REASON = "reason";

  private CodeReviewProfileJsonSchema() {
  }

  public static JsonNode schema() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(GENERAL_OBSERVATIONS, array(generalDecision()));
    properties.set(TAG_ASSESSMENTS, array(tagDecision()));
    require(root, GENERAL_OBSERVATIONS, TAG_ASSESSMENTS);
    return root;
  }

  private static ObjectNode generalDecision() {
    ObjectNode decision = commonDecision();
    decision.withObject("properties").set(DIMENSION, stringEnum(
        CodeReviewProfileConsumerConstants.GENERAL_DIMENSIONS.stream().map(Enum::name).toList()));
    require(decision, DIMENSION, ACTION, CONTENT, REASON);
    return decision;
  }

  private static ObjectNode tagDecision() {
    ObjectNode decision = commonDecision();
    decision.withObject("properties").set(TAG_ID, integer());
    require(decision, TAG_ID, ACTION, CONTENT, REASON);
    return decision;
  }

  private static ObjectNode commonDecision() {
    ObjectNode decision = object();
    decision.put("type", "object");
    decision.put("additionalProperties", false);
    ObjectNode properties = decision.putObject("properties");
    properties.set(ACTION, stringEnum(List.of(ProfileUpdateAction.NO_CHANGE.name(), ProfileUpdateAction.REPLACE.name())));
    properties.set(CONTENT, string());
    properties.set(REASON, string());
    return decision;
  }

  private static ObjectNode array(ObjectNode items) {
    ObjectNode array = object();
    array.put("type", "array");
    array.set("items", items);
    return array;
  }

  private static ObjectNode integer() {
    ObjectNode value = object();
    value.put("type", "integer");
    value.put("minimum", 1);
    return value;
  }

  private static ObjectNode string() {
    ObjectNode value = object();
    value.put("type", "string");
    return value;
  }

  private static ObjectNode stringEnum(List<String> values) {
    ObjectNode value = string();
    ArrayNode enumeration = value.putArray("enum");
    values.forEach(enumeration::add);
    return value;
  }

  private static ObjectNode object() {
    // OpenAI 严格 JSON Schema 要求根对象也显式声明 type。
    return JsonNodeFactory.instance.objectNode().put("type", "object");
  }

  private static void require(ObjectNode node, String... fields) {
    ArrayNode required = node.putArray("required");
    for (String field : fields) {
      required.add(field);
    }
  }
}
