package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionToolContracts;

/** 草案修订最终输出只引用 compile Tool 已生成的 artifact。 */
public final class LearningPlanDraftRevisionJsonSchema {

  private LearningPlanDraftRevisionJsonSchema() {
  }

  public static JsonNode schema() {
    ObjectNode root = object();
    root.put("additionalProperties", false);
    ObjectNode properties = root.putObject("properties");
    properties.set("status", enumString(LearningPlanRevisionToolContracts.FINAL_STATUS_COMPILED));
    properties.set("artifactRef", string());
    require(root, "status", "artifactRef");
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
