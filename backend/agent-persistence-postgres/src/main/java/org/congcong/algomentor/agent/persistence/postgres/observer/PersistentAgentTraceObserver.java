package org.congcong.algomentor.agent.persistence.postgres.observer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContextSnapshotMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContextSnapshotRow;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;

public class PersistentAgentTraceObserver implements AgentLoopObserver {

  static final String REDACTION_POLICY_VERSION = "agent-trace-redaction-v1";
  private static final String SNAPSHOT_POLICY_NAME = "final-request-snapshot";
  private static final String SNAPSHOT_POLICY_VERSION = "v1";

  private final AgentContextSnapshotMapper snapshotMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public PersistentAgentTraceObserver(AgentContextSnapshotMapper snapshotMapper, ObjectMapper objectMapper) {
    this(snapshotMapper, objectMapper, Clock.systemUTC());
  }

  public PersistentAgentTraceObserver(
      AgentContextSnapshotMapper snapshotMapper,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.snapshotMapper = snapshotMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  public void onLlmRequestReady(AgentLoopContext context, int stepIndex, LlmCompletionRequest request) {
    Long taskId = longMetadata(context, AgentRuntimeMetadataKeys.TASK_ID);
    Long runDbId = longMetadata(context, AgentRuntimeMetadataKeys.RUN_DB_ID);
    if (taskId == null || runDbId == null) {
      return;
    }

    JsonNode messages = redact(objectMapper.valueToTree(request.messages()));
    JsonNode tools = redact(objectMapper.valueToTree(request.tools()));
    JsonNode toolChoice = redact(objectMapper.valueToTree(request.toolChoice()));
    JsonNode generationOptions = redact(objectMapper.valueToTree(request.options()));
    JsonNode requestSnapshot = redact(requestSnapshot(request, messages, tools, toolChoice, generationOptions));
    String requestHash = sha256Hex(canonicalJson(requestSnapshot));
    Instant now = clock.instant();

    snapshotMapper.insertSnapshot(new ContextSnapshotRow(
        taskId,
        runDbId,
        stepIndex,
        context.request().requestId(),
        request.modelSelector().providerId().map(LlmProviderId::value).orElse(null),
        request.modelSelector().modelId().map(LlmModelId::value).orElse(null),
        request.modelSelector().purpose(),
        SNAPSHOT_POLICY_NAME,
        SNAPSHOT_POLICY_VERSION,
        intMetadata(context, AgentRuntimeMetadataKeys.TOKEN_BUDGET, 0),
        estimateTokens(messages),
        reservedOutputTokens(request),
        "inline",
        requestSnapshot,
        messages,
        tools,
        toolChoice,
        generationOptions,
        requestHash,
        REDACTION_POLICY_VERSION,
        redact(objectMapper.valueToTree(Map.of(
            "agentRunId", context.runId(),
            "requestMetadata", context.request().metadata()))),
        now));
  }

  private JsonNode redact(JsonNode node) {
    if (node == null || node.isNull()) {
      return objectMapper.nullNode();
    }
    JsonNode copy = node.deepCopy();
    redactInPlace(copy);
    return copy;
  }

  private JsonNode requestSnapshot(
      LlmCompletionRequest request,
      JsonNode messages,
      JsonNode tools,
      JsonNode toolChoice,
      JsonNode generationOptions
  ) {
    ObjectNode snapshot = objectMapper.createObjectNode();
    ObjectNode modelSelector = snapshot.putObject("modelSelector");
    request.modelSelector().providerId().map(LlmProviderId::value).ifPresent(value -> modelSelector.put("providerId", value));
    request.modelSelector().modelId().map(LlmModelId::value).ifPresent(value -> modelSelector.put("modelId", value));
    modelSelector.put("purpose", request.modelSelector().purpose());
    ArrayNode capabilities = modelSelector.putArray("requiredCapabilities");
    request.modelSelector().requiredCapabilities().stream()
        .map(Enum::name)
        .sorted()
        .forEach(capabilities::add);

    snapshot.set("messages", messages);
    snapshot.set("tools", tools);
    snapshot.set("toolChoice", toolChoice);
    snapshot.set("generationOptions", generationOptions);
    snapshot.set("responseFormat", objectMapper.valueToTree(request.responseFormat()));
    snapshot.set("metadata", objectMapper.valueToTree(request.metadata()));
    return snapshot;
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
    return normalized.contains("apikey")
        || normalized.contains("api_key")
        || normalized.contains("authorization")
        || normalized.contains("cookie")
        || normalized.contains("jwt")
        || normalized.contains("token")
        || normalized.contains("password")
        || normalized.contains("secret");
  }

  private String canonicalJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize agent trace JSON", ex);
    }
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 digest is unavailable", ex);
    }
  }

  private Long longMetadata(AgentLoopContext context, String key) {
    Object value = context.metadata().get(key);
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      return Long.parseLong(text);
    }
    return null;
  }

  private int intMetadata(AgentLoopContext context, String key, int defaultValue) {
    Object value = context.metadata().get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      return Integer.parseInt(text);
    }
    return defaultValue;
  }

  private Integer estimateTokens(JsonNode messages) {
    if (messages == null || messages.isNull()) {
      return null;
    }
    return Math.max(1, canonicalJson(messages).length() / 4);
  }

  private Integer reservedOutputTokens(LlmCompletionRequest request) {
    return request.options().maxOutputTokens() == null ? null : request.options().maxOutputTokens();
  }
}
