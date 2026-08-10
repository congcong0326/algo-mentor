package org.congcong.algomentor.mentor.application.profile.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperationBatch;

/** Code Review Claim operation 的 provider-native 严格 JSON Schema。 */
public final class LearnerMemoryCodeReviewJsonSchema {

  public static final String SCHEMA_NAME = "learner_memory_code_review_update_v2";
  public static final String OPERATIONS = "operations";
  public static final String ACTION = "action";
  public static final String SCOPE = "scope";
  public static final String KIND = "kind";
  public static final String DIMENSION = "dimension";
  public static final String TAG_ID = "tagId";
  public static final String TARGET_REVISION_ID = "targetRevisionId";
  public static final String CLAIM_TEXT = "claimText";
  public static final String PATTERN = "pattern";
  public static final String REASON = "reason";
  public static final String REVIEW_EVIDENCE = "reviewEvidence";
  public static final String REVIEW_ID = "reviewId";
  public static final String ROLE = "role";

  private LearnerMemoryCodeReviewJsonSchema() {
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
    array.put("maxItems", LearnerMemoryOperationBatch.MAX_OPERATIONS);
    ArrayNode variants = array.putObject("items").putArray("anyOf");
    variants.add(add());
    variants.add(confirm());
    variants.add(revise());
    variants.add(retire());
    return array;
  }

  private static ObjectNode add() {
    ObjectNode root = operation();
    ObjectNode properties = root.withObject("properties");
    properties.set(ACTION, stringEnum(List.of("ADD")));
    properties.set(SCOPE, scope());
    properties.set(CLAIM_TEXT, string(1, LearnerMemoryClaimContract.CLAIM_TEXT_TARGET_MAX_CHARS));
    addEvidence(properties);
    require(root, ACTION, SCOPE, CLAIM_TEXT, PATTERN, REASON, REVIEW_EVIDENCE);
    return root;
  }

  private static ObjectNode confirm() {
    ObjectNode root = operation();
    ObjectNode properties = root.withObject("properties");
    properties.set(ACTION, stringEnum(List.of("CONFIRM")));
    properties.set(TARGET_REVISION_ID, positiveInteger());
    addEvidence(properties);
    require(root, ACTION, TARGET_REVISION_ID, PATTERN, REASON, REVIEW_EVIDENCE);
    return root;
  }

  private static ObjectNode revise() {
    ObjectNode root = operation();
    ObjectNode properties = root.withObject("properties");
    properties.set(ACTION, stringEnum(List.of("REVISE")));
    properties.set(TARGET_REVISION_ID, positiveInteger());
    properties.set(CLAIM_TEXT, string(1, LearnerMemoryClaimContract.CLAIM_TEXT_TARGET_MAX_CHARS));
    addEvidence(properties);
    require(root, ACTION, TARGET_REVISION_ID, CLAIM_TEXT, PATTERN, REASON, REVIEW_EVIDENCE);
    return root;
  }

  private static ObjectNode retire() {
    ObjectNode root = operation();
    ObjectNode properties = root.withObject("properties");
    properties.set(ACTION, stringEnum(List.of("RETIRE")));
    properties.set(TARGET_REVISION_ID, positiveInteger());
    addEvidence(properties);
    require(root, ACTION, TARGET_REVISION_ID, PATTERN, REASON, REVIEW_EVIDENCE);
    return root;
  }

  private static ObjectNode operation() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    return root;
  }

  private static void addEvidence(ObjectNode properties) {
    properties.set(PATTERN, stringEnum(java.util.Arrays.stream(LearnerMemoryEvidenceContract.Pattern.values())
        .filter(value -> value != LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION
            && value != LearnerMemoryEvidenceContract.Pattern.USER_CORRECTION)
        .map(Enum::name)
        .toList()));
    properties.set(REASON, string(1, LearnerMemoryClaimContract.CLAIM_TEXT_MAX_CHARS));
    properties.set(REVIEW_EVIDENCE, reviewEvidence());
  }

  private static ObjectNode scope() {
    ObjectNode value = object();
    ArrayNode variants = value.putArray("anyOf");
    variants.add(generalScope());
    variants.add(tagScope());
    return value;
  }

  private static ObjectNode generalScope() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(KIND, stringEnum(List.of(LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION.name())));
    properties.set(DIMENSION, stringEnum(LearnerMemoryCodeReviewConsumerConstants.GENERAL_DIMENSIONS.stream()
        .map(Enum::name).toList()));
    require(root, KIND, DIMENSION);
    return root;
  }

  private static ObjectNode tagScope() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set(KIND, stringEnum(List.of(LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT.name())));
    properties.set(DIMENSION, stringEnum(List.of(LearnerMemoryClaimContract.Dimension.TAG_MASTERY.name())));
    properties.set(TAG_ID, positiveInteger());
    require(root, KIND, DIMENSION, TAG_ID);
    return root;
  }

  private static ObjectNode reviewEvidence() {
    ObjectNode array = object();
    array.put("type", "array");
    array.put("minItems", 1);
    array.put("maxItems", 20);
    ObjectNode item = object();
    item.put("additionalProperties", false);
    ObjectNode properties = item.putObject("properties");
    properties.set(REVIEW_ID, positiveInteger());
    properties.set(ROLE, stringEnum(java.util.Arrays.stream(LearnerMemoryEvidenceContract.ReviewRole.values())
        .map(Enum::name).toList()));
    require(item, REVIEW_ID, ROLE);
    array.set("items", item);
    return array;
  }

  private static ObjectNode string(int minLength, int maxLength) {
    ObjectNode value = object();
    value.put("type", "string");
    value.put("minLength", minLength);
    value.put("maxLength", maxLength);
    return value;
  }

  private static ObjectNode stringEnum(List<String> values) {
    ObjectNode value = string(1, Integer.MAX_VALUE);
    value.remove("maxLength");
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
    return JsonNodeFactory.instance.objectNode().put("type", "object");
  }

  private static void require(ObjectNode root, String... names) {
    ArrayNode required = root.putArray("required");
    for (String name : names) {
      required.add(name);
    }
  }
}
