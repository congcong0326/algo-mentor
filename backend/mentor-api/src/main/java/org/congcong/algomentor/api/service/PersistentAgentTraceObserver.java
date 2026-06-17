package org.congcong.algomentor.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.springframework.jdbc.core.JdbcOperations;

public class PersistentAgentTraceObserver implements AgentLoopObserver {

  static final String REDACTION_POLICY_VERSION = "agent-trace-redaction-v1";
  private static final String SNAPSHOT_POLICY_NAME = "final-request-snapshot";
  private static final String SNAPSHOT_POLICY_VERSION = "v1";

  private final JdbcOperations jdbcOperations;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public PersistentAgentTraceObserver(JdbcOperations jdbcOperations, ObjectMapper objectMapper) {
    this(jdbcOperations, objectMapper, Clock.systemUTC());
  }

  PersistentAgentTraceObserver(JdbcOperations jdbcOperations, ObjectMapper objectMapper, Clock clock) {
    this.jdbcOperations = jdbcOperations;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  public void onLlmRequestReady(AgentLoopContext context, int stepIndex, LlmCompletionRequest request) {
    Long taskId = longMetadata(context, "taskId");
    Long runDbId = longMetadata(context, "runDbId");
    if (taskId == null || runDbId == null) {
      return;
    }

    JsonNode requestSnapshot = redact(objectMapper.valueToTree(request));
    JsonNode messages = redact(objectMapper.valueToTree(request.messages()));
    JsonNode tools = redact(objectMapper.valueToTree(request.tools()));
    JsonNode toolChoice = redact(objectMapper.valueToTree(request.toolChoice()));
    JsonNode generationOptions = redact(objectMapper.valueToTree(request.options()));
    String requestHash = sha256Hex(canonicalJson(requestSnapshot));
    Instant now = clock.instant();

    jdbcOperations.update("""
        INSERT INTO agent_context_snapshot (
          task_id,
          run_id,
          step_index,
          request_id,
          provider,
          model,
          model_selector,
          policy_name,
          policy_version,
          token_budget,
          token_estimate,
          reserved_output_tokens,
          snapshot_storage_mode,
          request_snapshot_json,
          messages_json,
          tools_json,
          tool_choice_json,
          generation_options,
          request_hash,
          redaction_policy_version,
          metadata,
          created_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb, ?)
        """,
        taskId,
        runDbId,
        stepIndex,
        context.request().requestId(),
        request.modelSelector().providerId().map(LlmProviderId::value).orElse(null),
        request.modelSelector().modelId().map(LlmModelId::value).orElse(null),
        request.modelSelector().purpose(),
        SNAPSHOT_POLICY_NAME,
        SNAPSHOT_POLICY_VERSION,
        intMetadata(context, "tokenBudget", 0),
        estimateTokens(messages),
        reservedOutputTokens(request),
        "inline",
        canonicalJson(requestSnapshot),
        canonicalJson(messages),
        canonicalJson(tools),
        canonicalJson(toolChoice),
        canonicalJson(generationOptions),
        requestHash,
        REDACTION_POLICY_VERSION,
        canonicalJson(redact(objectMapper.valueToTree(Map.of(
            "agentRunId", context.runId(),
            "requestMetadata", context.request().metadata())))),
        Timestamp.from(now));
  }

  private JsonNode redact(JsonNode node) {
    if (node == null || node.isNull()) {
      return objectMapper.nullNode();
    }
    JsonNode copy = node.deepCopy();
    redactInPlace(copy, "");
    return copy;
  }

  private void redactInPlace(JsonNode node, String fieldName) {
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
          redactInPlace(field.getValue(), field.getKey());
        }
      }
      return;
    }
    if (node.isArray()) {
      ArrayNode array = (ArrayNode) node;
      for (JsonNode item : array) {
        redactInPlace(item, fieldName);
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
