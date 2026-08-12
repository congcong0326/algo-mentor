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
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.prompt.AgentPromptMetadataKeys;
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

    JsonNode messages = auditMessages(request.messages());
    JsonNode tools = redact(objectMapper.valueToTree(request.tools()));
    JsonNode toolChoice = redact(objectMapper.valueToTree(request.toolChoice()));
    JsonNode generationOptions = redact(objectMapper.valueToTree(request.options()));
    Map<String, Object> auditMetadata = auditMetadata(context, request, messages, tools);
    JsonNode requestSnapshot = redact(requestSnapshot(
        request, messages, tools, toolChoice, generationOptions, auditMetadata));
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
        promptBudget(context, request),
        integer(auditMetadata.get(AgentRuntimeMetadataKeys.FINAL_REQUEST_TOKEN_ESTIMATE)),
        reservedOutputTokens(request),
        "inline",
        requestSnapshot,
        messages,
        tools,
        toolChoice,
        generationOptions,
        requestHash,
        REDACTION_POLICY_VERSION,
        redact(objectMapper.valueToTree(auditMetadata)),
        now));
    if (runTraceMapper != null) {
      runTraceMapper.attachRequestSnapshot(runDbId, stepIndex, snapshotId);
    }
  }

  private JsonNode redact(JsonNode node) {
    return redactor.redact(node);
  }

  /**
   * Provider request 本身不消费来源标签；仅在持久化的审计副本上补充可验证的来源信息。
   */
  private JsonNode auditMessages(java.util.List<org.congcong.algomentor.llm.core.request.LlmMessage> requestMessages) {
    JsonNode snapshot = redact(objectMapper.valueToTree(requestMessages));
    if (!(snapshot instanceof ArrayNode messages)) {
      return snapshot;
    }
    for (int index = 0; index < requestMessages.size() && index < messages.size(); index++) {
      if (!(messages.get(index) instanceof ObjectNode message)) {
        continue;
      }
      auditMessageSource(requestMessages.get(index)).ifPresent(source ->
          message.put(AgentPromptMetadataKeys.AUDIT_MESSAGE_SOURCE, source));
    }
    return messages;
  }

  private java.util.Optional<String> auditMessageSource(
      org.congcong.algomentor.llm.core.request.LlmMessage message
  ) {
    Object source = message.metadata().get(AgentPromptMetadataKeys.AUDIT_MESSAGE_SOURCE);
    if (source instanceof String value && isPromptTrustLevel(value)) {
      return java.util.Optional.of(value);
    }
    if (message.role() == org.congcong.algomentor.llm.core.request.LlmMessage.Role.TOOL) {
      return java.util.Optional.of("TOOL_OUTPUT");
    }
    return java.util.Optional.of(switch (message.role()) {
      case SYSTEM -> "SYSTEM_STATIC";
      case USER -> "USER_INPUT";
      case ASSISTANT -> "MODEL_GENERATED";
      case TOOL -> "TOOL_OUTPUT";
    });
  }

  private boolean isPromptTrustLevel(String value) {
    try {
      org.congcong.algomentor.agent.core.prompt.PromptTrustLevel.valueOf(value);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private JsonNode requestSnapshot(
      LlmCompletionRequest request,
      JsonNode messages,
      JsonNode tools,
      JsonNode toolChoice,
      JsonNode generationOptions,
      Map<String, Object> auditMetadata
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
    snapshot.set(AgentTraceJsonKeys.METADATA, objectMapper.valueToTree(auditMetadata));
    return snapshot;
  }

  private Map<String, Object> auditMetadata(
      AgentLoopContext context,
      LlmCompletionRequest request,
      JsonNode messages,
      JsonNode tools
  ) {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put(AgentRuntimeMetadataKeys.AGENT_RUN_ID, context.runId());
    values.put(AgentRuntimeMetadataKeys.REQUEST_METADATA, context.request().metadata());
    values.putAll(request.metadata());

    int messageTokenEstimate = estimateTokens(messages);
    int toolsTokenEstimate = estimateTokens(tools);
    int providerOverhead = providerOverhead(request, messages);
    int finalEstimate = messageTokenEstimate + toolsTokenEstimate + providerOverhead;
    int promptBudget = promptBudget(context, request);
    int assemblyEstimate = assemblyEstimate(context, request);

    values.put(AgentRuntimeMetadataKeys.ASSEMBLY_TOKEN_ESTIMATE, assemblyEstimate);
    values.put(AgentRuntimeMetadataKeys.MESSAGE_TOKEN_ESTIMATE, messageTokenEstimate);
    values.put(AgentRuntimeMetadataKeys.TOOLS_TOKEN_ESTIMATE, toolsTokenEstimate);
    values.put(AgentRuntimeMetadataKeys.PROVIDER_OVERHEAD_TOKEN_ESTIMATE, providerOverhead);
    values.put(AgentRuntimeMetadataKeys.FINAL_REQUEST_TOKEN_ESTIMATE, finalEstimate);
    values.put("messageCount", request.messages().size());
    values.put("toolsCount", request.tools().size());
    values.put(AgentRuntimeMetadataKeys.BUDGET_STATUS,
        budgetStatus(promptBudget, finalEstimate));
    values.put(AgentRuntimeMetadataKeys.OVER_BUDGET_TOKENS,
        promptBudget > 0 ? Math.max(0, finalEstimate - promptBudget) : 0);
    enrichCompaction(values, messages);
    return Map.copyOf(values);
  }

  private void enrichCompaction(Map<String, Object> values, JsonNode messages) {
    Object compaction = values.get(AgentRuntimeMetadataKeys.RUN_CONTEXT_COMPACTION);
    if (compaction instanceof Map<?, ?> metadata) {
      metadata.forEach((key, value) -> {
        if (key instanceof String name) {
          values.put(name, value);
        }
      });
    }
    int previewCount = 0;
    if (messages != null && messages.isArray()) {
      for (JsonNode message : messages) {
        String serialized = message.toString();
        if (serialized.contains("\"type\":\"tool_result_preview\"")) {
          previewCount++;
        }
      }
    }
    List<String> actions = compactionActions(values.get(AgentRuntimeMetadataKeys.COMPACTION_ACTIONS));
    appendPromptBudgetActions(values, actions);
    if (previewCount > 0 && !actions.contains("tool_result_preview")) {
      actions.add("tool_result_preview");
    }
    values.put(AgentRuntimeMetadataKeys.COMPACTION_APPLIED,
        Boolean.TRUE.equals(values.get(AgentRuntimeMetadataKeys.COMPACTION_APPLIED)) || !actions.isEmpty());
    values.put(AgentRuntimeMetadataKeys.COMPACTION_ACTIONS, List.copyOf(actions));
    values.put(AgentRuntimeMetadataKeys.TOOL_RESULT_PREVIEW_COUNT, previewCount);
    values.putIfAbsent(AgentRuntimeMetadataKeys.TOOL_RESULT_COMPACTED_COUNT, 0);
    values.putIfAbsent(AgentRuntimeMetadataKeys.SNIPPED_MESSAGE_GROUP_COUNT, 0);
  }

  private List<String> compactionActions(Object value) {
    List<String> actions = new ArrayList<>();
    if (value instanceof Iterable<?> entries) {
      for (Object entry : entries) {
        if (entry instanceof String action && !action.isBlank() && !actions.contains(action)) {
          actions.add(action);
        }
      }
    }
    return actions;
  }

  private void appendPromptBudgetActions(Map<String, Object> values, List<String> actions) {
    Object value = values.get(AgentPromptMetadataKeys.PROMPT_SECTION_ACTIONS);
    if (!(value instanceof Map<?, ?> sectionActions)) {
      return;
    }
    for (Object action : sectionActions.values()) {
      if (!(action instanceof String name)) {
        continue;
      }
      String auditAction = switch (name) {
        case "DROP" -> "section_drop";
        case "TRUNCATE" -> "section_truncate";
        case "EXTRACT" -> "section_extract";
        case "SUMMARIZE" -> "summary";
        default -> null;
      };
      if (auditAction != null && !actions.contains(auditAction)) {
        actions.add(auditAction);
      }
    }
  }

  private String budgetStatus(int promptBudget, int finalEstimate) {
    if (promptBudget < 1) {
      return "UNKNOWN_PROVIDER_USAGE";
    }
    return finalEstimate > promptBudget ? "ESTIMATE_OVER_BUDGET" : "WITHIN_ESTIMATE";
  }

  private int providerOverhead(LlmCompletionRequest request, JsonNode messages) {
    int messageCount = messages != null && messages.isArray() ? messages.size() : 0;
    int toolCount = request.tools() == null ? 0 : request.tools().size();
    return 3 + messageCount * 3 + (toolCount > 0 ? 8 : 0);
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
    return intMetadata(context.metadata(), key, defaultValue);
  }

  private int promptBudget(AgentLoopContext context, LlmCompletionRequest request) {
    return intMetadata(context, AgentRuntimeMetadataKeys.TOKEN_BUDGET,
        intMetadata(context, AgentPromptMetadataKeys.PROMPT_TOKEN_BUDGET,
            intMetadata(request.metadata(), AgentPromptMetadataKeys.PROMPT_TOKEN_BUDGET, 0)));
  }

  private int assemblyEstimate(AgentLoopContext context, LlmCompletionRequest request) {
    return intMetadata(context, AgentRuntimeMetadataKeys.TOKEN_ESTIMATE,
        intMetadata(context, AgentPromptMetadataKeys.PROMPT_TOKEN_ESTIMATE,
            intMetadata(request.metadata(), AgentPromptMetadataKeys.PROMPT_TOKEN_ESTIMATE, 0)));
  }

  private int intMetadata(Map<String, Object> metadata, String key, int defaultValue) {
    Object value = metadata == null ? null : metadata.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      return Integer.parseInt(text);
    }
    return defaultValue;
  }

  private int estimateTokens(JsonNode value) {
    if (value == null || value.isNull()) {
      return 0;
    }
    return Math.max(0, canonicalJson(value).length() / 4);
  }

  private Integer integer(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }

  private Integer reservedOutputTokens(LlmCompletionRequest request) {
    return request.options().maxOutputTokens() == null ? null : request.options().maxOutputTokens();
  }
}
