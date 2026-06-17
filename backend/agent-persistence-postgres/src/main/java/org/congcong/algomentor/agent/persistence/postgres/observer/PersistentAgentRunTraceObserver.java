package org.congcong.algomentor.agent.persistence.postgres.observer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStepResult;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunTraceMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepEndUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepStartRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallEndUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStartRow;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public class PersistentAgentRunTraceObserver implements AgentLoopObserver {

  private final AgentRunTraceMapper runTraceMapper;
  private final ObjectMapper objectMapper;
  private final AgentTraceRedactor redactor;
  private final Clock clock;
  private final Map<String, TraceBuffer> buffers = new ConcurrentHashMap<>();

  public PersistentAgentRunTraceObserver(AgentRunTraceMapper runTraceMapper, ObjectMapper objectMapper) {
    this(runTraceMapper, objectMapper, Clock.systemUTC());
  }

  public PersistentAgentRunTraceObserver(
      AgentRunTraceMapper runTraceMapper,
      ObjectMapper objectMapper,
      Clock clock
  ) {
    this.runTraceMapper = runTraceMapper;
    this.objectMapper = objectMapper;
    this.redactor = new AgentTraceRedactor(objectMapper);
    this.clock = clock;
  }

  @Override
  public void onRunStart(AgentLoopContext context) {
    if (runDbId(context) == null) {
      return;
    }
    buffers.put(context.runId(), new TraceBuffer());
  }

  @Override
  public void onStepStart(AgentLoopContext context, int stepIndex) {
    Long taskId = longMetadata(context, AgentRuntimeMetadataKeys.TASK_ID);
    Long runDbId = runDbId(context);
    if (taskId == null || runDbId == null) {
      return;
    }
    traceBuffer(context).steps.put(stepIndex, new StepBuffer());
    runTraceMapper.insertStepStart(new RunStepStartRow(
        taskId,
        runDbId,
        stepIndex,
        "running",
        redactedJson(Map.of("agentRunId", context.runId())),
        clock.instant()));
  }

  @Override
  public void onLlmEvent(AgentLoopContext context, int stepIndex, LlmStreamEvent event) {
    TraceBuffer traceBuffer = buffers.get(context.runId());
    if (traceBuffer == null) {
      return;
    }
    StepBuffer stepBuffer = traceBuffer.steps.computeIfAbsent(stepIndex, ignored -> new StepBuffer());
    if (event instanceof LlmStreamEvent.MessageStart start) {
      stepBuffer.provider = start.provider() == null ? null : start.provider().value();
      stepBuffer.model = start.model() == null ? null : start.model().value();
    }
    if (event instanceof LlmStreamEvent.Usage usage) {
      stepBuffer.usage = usage.usage();
    }
  }

  @Override
  public void onStepEnd(AgentLoopContext context, int stepIndex, AgentStepResult result) {
    Long runDbId = runDbId(context);
    if (runDbId == null) {
      return;
    }
    StepBuffer stepBuffer = stepBuffer(context, stepIndex);
    runTraceMapper.markStepSucceeded(new RunStepEndUpdate(
        runDbId,
        stepIndex,
        "succeeded",
        stepBuffer.provider,
        stepBuffer.model,
        result.finishReason().name(),
        jsonNode(usageMap(stepBuffer.usage)),
        clock.instant()));
  }

  @Override
  public void onToolStart(AgentLoopContext context, int stepIndex, LlmToolCall toolCall) {
    Long taskId = longMetadata(context, AgentRuntimeMetadataKeys.TASK_ID);
    Long runDbId = runDbId(context);
    if (taskId == null || runDbId == null) {
      return;
    }
    Instant now = clock.instant();
    traceBuffer(context).toolStartedAt.put(toolKey(stepIndex, toolCall.id()), now);
    JsonNode arguments = redactor.redact(toolCall.arguments());
    runTraceMapper.insertToolStart(new ToolCallStartRow(
        taskId,
        runDbId,
        stepIndex,
        toolCall.id(),
        toolCall.name(),
        arguments,
        "running",
        charCount(arguments),
        tokenEstimate(arguments),
        AgentTraceRedactor.POLICY_VERSION,
        redactedJson(Map.of("agentRunId", context.runId())),
        now));
  }

  @Override
  public void onToolEnd(AgentLoopContext context, int stepIndex, LlmToolCall toolCall, JsonNode result) {
    Long runDbId = runDbId(context);
    if (runDbId == null) {
      return;
    }
    Instant endedAt = clock.instant();
    JsonNode redactedResult = redactor.redact(result);
    ToolStorageMetadata storageMetadata = storageMetadata(redactedResult);
    runTraceMapper.markToolSucceeded(new ToolCallEndUpdate(
        runDbId,
        stepIndex,
        toolCall.id(),
        "succeeded",
        redactedResult,
        durationMillis(context, stepIndex, toolCall.id(), endedAt),
        charCount(redactedResult),
        tokenEstimate(redactedResult),
        storageMetadata.resultLineCount(),
        storageMetadata.resultSha256(),
        storageMetadata.resultStorageMode(),
        storageMetadata.resultBlobId(),
        storageMetadata.resultPreviewJson(),
        storageMetadata.resultRef(),
        endedAt));
  }

  @Override
  public void onToolError(AgentLoopContext context, int stepIndex, LlmToolCall toolCall, AgentException error) {
    Long runDbId = runDbId(context);
    if (runDbId == null) {
      return;
    }
    Instant endedAt = clock.instant();
    runTraceMapper.markToolFailed(new ToolCallErrorUpdate(
        runDbId,
        stepIndex,
        toolCall.id(),
        "failed",
        redactedJson(errorMap(error)),
        durationMillis(context, stepIndex, toolCall.id(), endedAt),
        endedAt));
  }

  @Override
  public void onError(AgentLoopContext context, AgentException error) {
    Long runDbId = runDbId(context);
    if (runDbId == null) {
      return;
    }
    TraceBuffer traceBuffer = buffers.remove(context.runId());
    if (traceBuffer == null || traceBuffer.steps.isEmpty()) {
      return;
    }
    int latestStep = traceBuffer.steps.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    runTraceMapper.markStepFailed(new RunStepErrorUpdate(
        runDbId,
        latestStep,
        "failed",
        redactedJson(errorMap(error)),
        clock.instant()));
  }

  @Override
  public void onRunEnd(AgentLoopContext context, AgentRunResult result) {
    buffers.remove(context.runId());
  }

  private TraceBuffer traceBuffer(AgentLoopContext context) {
    return buffers.computeIfAbsent(context.runId(), ignored -> new TraceBuffer());
  }

  private StepBuffer stepBuffer(AgentLoopContext context, int stepIndex) {
    return traceBuffer(context).steps.computeIfAbsent(stepIndex, ignored -> new StepBuffer());
  }

  private Long runDbId(AgentLoopContext context) {
    return longMetadata(context, AgentRuntimeMetadataKeys.RUN_DB_ID);
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

  private JsonNode jsonNode(Object value) {
    return objectMapper.valueToTree(value);
  }

  private JsonNode redactedJson(Object value) {
    return redactor.redact(jsonNode(value));
  }

  private Map<String, Object> usageMap(LlmUsage usage) {
    if (usage == null) {
      return Map.of();
    }
    Map<String, Object> values = new HashMap<>();
    values.put("inputTokens", usage.inputTokens());
    values.put("outputTokens", usage.outputTokens());
    values.put("cachedTokens", usage.cachedTokens());
    values.put("reasoningTokens", usage.reasoningTokens());
    values.put("totalTokens", usage.totalTokens());
    return values;
  }

  private Map<String, Object> errorMap(AgentException error) {
    return Map.of(
        "code", error.code().name(),
        "message", error.getMessage(),
        "retryable", error.retryable(),
        "metadata", error.metadata());
  }

  private String toolKey(int stepIndex, String toolCallId) {
    return stepIndex + ":" + toolCallId;
  }

  private Long durationMillis(AgentLoopContext context, int stepIndex, String toolCallId, Instant endedAt) {
    Instant startedAt = traceBuffer(context).toolStartedAt.remove(toolKey(stepIndex, toolCallId));
    return startedAt == null ? null : Duration.between(startedAt, endedAt).toMillis();
  }

  private Integer charCount(JsonNode node) {
    return canonicalJson(node).length();
  }

  private Integer tokenEstimate(JsonNode node) {
    return Math.max(1, charCount(node) / 4);
  }

  private ToolStorageMetadata storageMetadata(JsonNode result) {
    if (result == null || !result.isObject()) {
      return ToolStorageMetadata.empty();
    }
    if (!"tool_result_preview".equals(textField(result, "type"))) {
      return ToolStorageMetadata.empty();
    }
    Long blobId = blobId(textField(result, "resultRef"));
    return new ToolStorageMetadata(
        "blob",
        blobId,
        result,
        textField(result, "resultRef"),
        intField(result, "lineCount"),
        null);
  }

  private Long blobId(String resultRef) {
    if (resultRef == null || !resultRef.startsWith("tool-result:")) {
      return null;
    }
    try {
      return Long.parseLong(resultRef.substring("tool-result:".length()));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private String textField(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    return value == null || !value.isTextual() ? null : value.asText();
  }

  private Integer intField(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    return value == null || !value.canConvertToInt() ? null : value.asInt();
  }

  private String canonicalJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize agent trace JSON", ex);
    }
  }

  private static final class TraceBuffer {
    private final Map<Integer, StepBuffer> steps = new ConcurrentHashMap<>();
    private final Map<String, Instant> toolStartedAt = new ConcurrentHashMap<>();
  }

  private static final class StepBuffer {
    private LlmUsage usage;
    private String provider;
    private String model;
  }

  private record ToolStorageMetadata(
      String resultStorageMode,
      Long resultBlobId,
      JsonNode resultPreviewJson,
      String resultRef,
      Integer resultLineCount,
      String resultSha256
  ) {

    private static ToolStorageMetadata empty() {
      return new ToolStorageMetadata(null, null, null, null, null, null);
    }
  }
}
