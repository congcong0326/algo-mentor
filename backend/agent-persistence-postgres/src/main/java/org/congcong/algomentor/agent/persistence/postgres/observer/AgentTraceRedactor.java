package org.congcong.algomentor.agent.persistence.postgres.observer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

final class AgentTraceRedactor {

  static final String POLICY_VERSION = "agent-trace-redaction-v1";

  private final ObjectMapper objectMapper;

  AgentTraceRedactor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  JsonNode redact(JsonNode node) {
    if (node == null || node.isNull()) {
      return objectMapper.nullNode();
    }
    JsonNode copy = node.deepCopy();
    redactInPlace(copy);
    return copy;
  }

  private void redactInPlace(JsonNode node) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isObject()) {
      ObjectNode object = (ObjectNode) node;
      Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        if (isSensitiveField(field.getKey())) {
          object.put(field.getKey(), "[REDACTED]");
        } else {
          redactInPlace(field.getValue());
        }
      }
      return;
    }
    if (node.isArray()) {
      ArrayNode array = (ArrayNode) node;
      for (JsonNode item : array) {
        redactInPlace(item);
      }
    }
  }

  private boolean isSensitiveField(String fieldName) {
    String normalized = fieldName.toLowerCase(Locale.ROOT);
    String compact = normalized.replace("_", "").replace("-", "");
    return normalized.contains("apikey")
        || normalized.contains("api_key")
        || normalized.contains("api-key")
        || normalized.contains("authorization")
        || normalized.contains("cookie")
        || normalized.contains("set-cookie")
        || normalized.contains("jwt")
        || normalized.contains("bearer")
        || normalized.contains("token")
        || normalized.contains("access_token")
        || normalized.contains("refresh_token")
        || normalized.contains("oauth")
        || normalized.contains("password")
        || normalized.contains("passwd")
        || normalized.contains("secret");
  }
}
