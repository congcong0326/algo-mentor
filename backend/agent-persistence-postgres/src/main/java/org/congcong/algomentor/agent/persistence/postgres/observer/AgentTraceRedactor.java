package org.congcong.algomentor.agent.persistence.postgres.observer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Agent 诊断数据的统一脱敏器。
 *
 * <p>写入和管理员审计读取均使用此规则，避免旧数据或旁路写入绕过凭据脱敏。</p>
 */
public final class AgentTraceRedactor {

  public static final String POLICY_VERSION = "agent-trace-redaction-v1";

  private final ObjectMapper objectMapper;

  public AgentTraceRedactor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public JsonNode redact(JsonNode node) {
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
    if (isObservableTokenMetric(normalized.replace("_", "").replace("-", ""))) {
      return false;
    }
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

  /** Token 计数和预算是审计数值，不是凭据；其余 token 命名字段仍按凭据字段处理。 */
  private boolean isObservableTokenMetric(String fieldName) {
    return fieldName.equals("tokenbudget")
        || fieldName.equals("tokenestimate")
        || fieldName.equals("prompttokenbudget")
        || fieldName.equals("prompttokenestimate")
        || fieldName.equals("assemblytokenestimate")
        || fieldName.equals("messagetokenestimate")
        || fieldName.equals("toolstokenestimate")
        || fieldName.equals("provideroverheadtokenestimate")
        || fieldName.equals("finalrequesttokenestimate")
        || fieldName.equals("inputtokens")
        || fieldName.equals("actualinputtokens")
        || fieldName.equals("cachedtokens")
        || fieldName.equals("outputtokens")
        || fieldName.equals("reasoningtokens")
        || fieldName.equals("totaltokens")
        || fieldName.equals("overbudgettokens")
        || fieldName.equals("compactionbeforetokenestimate")
        || fieldName.equals("compactionaftertokenestimate")
        || fieldName.equals("argumenttokenestimate")
        || fieldName.equals("resulttokenestimate")
        || fieldName.equals("reservedoutputtokens");
  }
}
