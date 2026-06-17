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
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentTraceJsonKeys;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContextSnapshotMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunTraceMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContextSnapshotRow;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;

public class PersistentAgentTraceObserver implements AgentLoopObserver {

  static final String REDACTION_POLICY_VERSION = AgentTraceRedactor.POLICY_VERSION;
  private static final String SNAPSHOT_POLICY_NAME = "final-request-snapshot";
  private static final String SNAPSHOT_POLICY_VERSION = "v1";

  private final AgentContextSnapshotMapper snapshotMapper;
  private final AgentRunTraceMapper runTraceMapper;
  private final ObjectMapper objectMapper;
  private final AgentTraceRedactor redactor;
  private final Clock clock;

  public PersistentAgentTraceObserver(AgentContextSnapshotMapper snapshotMapper, ObjectMapper objectMapper) {
    this(snapshotMapper, null, objectMapper, Clock.systemUTC());
  }

  public PersistentAgentTraceObserver(
      AgentContextSnapshotMapper snapshotMapper,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this(snapshotMapper, null, objectMapper, clock);
  }

  public PersistentAgentTraceObserver(
      AgentContextSnapshotMapper snapshotMapper,
      AgentRunTraceMapper runTraceMapper,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.snapshotMapper = snapshotMapper;
    this.runTraceMapper = runTraceMapper;
    this.objectMapper = objectMapper;
    this.redactor = new AgentTraceRedactor(objectMapper);
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

    long snapshotId = snapshotMapper.insertSnapshot(new ContextSnapshotRow(
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
            AgentRuntimeMetadataKeys.AGENT_RUN_ID, context.runId(),
            AgentRuntimeMetadataKeys.REQUEST_METADATA, context.request().metadata()))),
        now));
    if (runTraceMapper != null) {
      runTraceMapper.attachRequestSnapshot(runDbId, stepIndex, snapshotId);
    }
  }

  private JsonNode redact(JsonNode node) {
    return redactor.redact(node);
  }

  private JsonNode requestSnapshot(
      LlmCompletionRequest request,
      JsonNode messages,
      JsonNode tools,
      JsonNode toolChoice,
      JsonNode generationOptions
  ) {
    ObjectNode snapshot = objectMapper.createObjectNode();
    ObjectNode modelSelector = snapshot.putObject(AgentTraceJsonKeys.MODEL_SELECTOR);
    request.modelSelector().providerId().map(LlmProviderId::value)
        .ifPresent(value -> modelSelector.put(AgentTraceJsonKeys.PROVIDER_ID, value));
    request.modelSelector().modelId().map(LlmModelId::value)
        .ifPresent(value -> modelSelector.put(AgentTraceJsonKeys.MODEL_ID, value));
    modelSelector.put(AgentTraceJsonKeys.PURPOSE, request.modelSelector().purpose());
    ArrayNode capabilities = modelSelector.putArray(AgentTraceJsonKeys.REQUIRED_CAPABILITIES);
    request.modelSelector().requiredCapabilities().stream()
        .map(Enum::name)
        .sorted()
        .forEach(capabilities::add);

    snapshot.set(AgentTraceJsonKeys.MESSAGES, messages);
    snapshot.set(AgentTraceJsonKeys.TOOLS, tools);
    snapshot.set(AgentTraceJsonKeys.TOOL_CHOICE, toolChoice);
    snapshot.set(AgentTraceJsonKeys.GENERATION_OPTIONS, generationOptions);
    snapshot.set(AgentTraceJsonKeys.RESPONSE_FORMAT, objectMapper.valueToTree(request.responseFormat()));
    snapshot.set(AgentTraceJsonKeys.METADATA, objectMapper.valueToTree(request.metadata()));
    return snapshot;
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
