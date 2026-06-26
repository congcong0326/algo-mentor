package org.congcong.algomentor.api.agent.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public record AgentToolPermissionDecisionRequest(
    String decision,
    String reason
) {

  public static final String DECISION_FIELD = "decision";
  public static final String REASON_FIELD = "reason";
  private static final Set<String> ALLOWED_FIELDS = Set.of(DECISION_FIELD, REASON_FIELD);

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static AgentToolPermissionDecisionRequest fromJson(JsonNode json) {
    if (json == null || !json.isObject()) {
      throw new IllegalArgumentException("工具权限决策请求体必须是 JSON 对象。");
    }
    rejectUnknownFields(json);
    return new AgentToolPermissionDecisionRequest(
        textOrNull(json, DECISION_FIELD),
        textOrNull(json, REASON_FIELD));
  }

  private static void rejectUnknownFields(JsonNode json) {
    Iterator<String> fieldNames = json.fieldNames();
    Set<String> unknownFields = new HashSet<>();
    while (fieldNames.hasNext()) {
      String fieldName = fieldNames.next();
      if (!ALLOWED_FIELDS.contains(fieldName)) {
        unknownFields.add(fieldName);
      }
    }
    if (!unknownFields.isEmpty()) {
      throw new IllegalArgumentException("工具权限决策请求体只允许包含 decision 和 reason。");
    }
  }

  private static String textOrNull(JsonNode json, String fieldName) {
    JsonNode value = json.get(fieldName);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isTextual()) {
      throw new IllegalArgumentException("工具权限决策字段必须是字符串。");
    }
    return value.asText();
  }
}
