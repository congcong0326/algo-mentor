package org.congcong.algomentor.ops.observability;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecision;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentOpsObserver implements AgentLoopObserver {

  private static final Logger log = LoggerFactory.getLogger(AgentOpsObserver.class);
  private static final AgentOpsSource FALLBACK_SOURCE = AgentOpsSource.AGENT_CONVERSATION;
  /** Synthetic permission decision used when a pending approval expires. */
  private static final String PERMISSION_TIMEOUT_DECISION = "timeout";
  private static final long NOT_RECORDED = -1L;
  private static final Set<String> SAFE_TOOL_ARGUMENT_FIELDS = Set.of(
      "company",
      "difficulty",
      "includeCounts",
      "limit",
      "lineEnd",
      "lineStart",
      "locale",
      "offset",
      "page",
      "pageSize",
      "recencyBucket",
      "role",
      "sort",
      "tag");
  private static final Set<String> SAFE_TOOL_RESULT_NUMBER_FIELDS = Set.of(
      "arrayLength",
      "charCount",
      "lineCount",
      "page",
      "pageSize",
      "problemCount",
      "total");
  private static final Set<String> SAFE_TOOL_RESULT_BOOLEAN_FIELDS = Set.of(
      "hasMoreAfter",
      "hasMoreBefore",
      "truncated");

  private final AgentOpsRecorder agent;
  private final StructuredOpsLogger opsLogger;
  private final LongSupplier nanoTime;
  private final ConcurrentMap<String, RunTiming> runTimings = new ConcurrentHashMap<>();
  private final ConcurrentMap<ToolCallKey, Long> toolStartNanos = new ConcurrentHashMap<>();

  public AgentOpsObserver(AgentOpsRecorder agent) {
    this(agent, new StructuredOpsLogger(), System::nanoTime);
  }

  AgentOpsObserver(AgentOpsRecorder agent, StructuredOpsLogger opsLogger) {
    this(agent, opsLogger, System::nanoTime);
  }

  AgentOpsObserver(
      AgentOpsRecorder agent,
      StructuredOpsLogger opsLogger,
      LongSupplier nanoTime
  ) {
    this.agent = Objects.requireNonNull(agent, "agent must not be null");
    this.opsLogger = Objects.requireNonNull(opsLogger, "opsLogger must not be null");
    this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
  }

  @Override
  public void onRunStart(AgentLoopContext context) {
    AgentOpsSource agentSource = source(context);
    runTimings.putIfAbsent(context.runId(), new RunTiming(now()));
    agent.runStarted(agentSource);
    Map<String, Object> fields = baseFields(context, agentSource);
    fields.put(OpsLogFields.MAX_STEPS, context.maxSteps());
    opsLogger.info(log, OpsLogEventType.AGENT_RUN_STARTED, fields);
  }

  @Override
  public void onStepStart(AgentLoopContext context, int stepIndex) {
    timing(context, stepIndex).markStepStarted(now());
  }

  @Override
  public void onLlmRequestReady(
      AgentLoopContext context,
      int stepIndex,
      LlmCompletionRequest request
  ) {
    long requestStartedNanos = now();
    StepTiming timing = timing(context, stepIndex);
    timing.markLlmRequestStarted(requestStartedNanos);
    Map<String, Object> fields = baseFields(context, source(context));
    fields.put(OpsLogFields.STEP_INDEX, stepIndex);
    fields.put(OpsLogFields.MESSAGE_COUNT, request.messages().size());
    fields.put(OpsLogFields.DECLARED_TOOL_COUNT, request.tools().size());
    opsLogger.info(log, OpsLogEventType.AGENT_LLM_REQUEST_STARTED, fields);
  }

  @Override
  public void onLlmEvent(AgentLoopContext context, int stepIndex, LlmStreamEvent event) {
    StepTiming timing = timing(context, stepIndex);
    timing.recordFirstEvent(now());
    if (event instanceof LlmStreamEvent.MessageStart messageStart) {
      timing.provider = messageStart.provider() == null ? null : messageStart.provider().value();
      timing.model = messageStart.model() == null ? null : messageStart.model().value();
    } else if (event instanceof LlmStreamEvent.Usage usage) {
      timing.usage = usage.usage();
    }
  }

  @Override
  public void onStepEnd(AgentLoopContext context, int stepIndex, org.congcong.algomentor.agent.core.AgentStepResult result) {
    RunTiming runTiming = runTimings.get(context.runId());
    StepTiming timing = runTiming == null ? null : runTiming.stepTimings.remove(stepIndex);
    long endedAtNanos = now();
    Map<String, Object> fields = baseFields(context, source(context));
    fields.put(OpsLogFields.STEP_INDEX, stepIndex);
    fields.put(OpsLogFields.FINISH_REASON, result.finishReason().name());
    fields.put(OpsLogFields.TOOL_CALL_COUNT, result.toolCalls().size());
    fields.put(OpsLogFields.OUTPUT_CHAR_COUNT, result.content().length());
    if (timing != null) {
      fields.put(OpsLogFields.DURATION_MS, elapsedMillis(timing.llmRequestStartedNanos, endedAtNanos));
      fields.put(OpsLogFields.CONTEXT_PREPARATION_MS, elapsedMillis(
          timing.stepStartedNanos,
          timing.llmRequestStartedNanos));
      long firstEventNanos = timing.firstEventNanos.get();
      if (firstEventNanos != NOT_RECORDED) {
        fields.put(OpsLogFields.TIME_TO_FIRST_EVENT_MS, elapsedMillis(
            timing.llmRequestStartedNanos,
            firstEventNanos));
      }
      putIfNonBlank(fields, OpsLogFields.PROVIDER, timing.provider);
      putIfNonBlank(fields, OpsLogFields.MODEL, timing.model);
      putUsage(fields, timing.usage);
    }
    opsLogger.info(log, OpsLogEventType.AGENT_LLM_STEP_COMPLETED, fields);
  }

  @Override
  public void onRunEnd(AgentLoopContext context, AgentRunResult result) {
    AgentOpsSource agentSource = source(context);
    RunTiming timing = runTimings.remove(context.runId());
    agent.runCompleted(agentSource);
    Map<String, Object> fields = baseFields(context, agentSource);
    fields.put(OpsLogFields.STEP_INDEX, result.steps());
    fields.put(OpsLogFields.FINISH_REASON, result.finishReason().name());
    if (result.output() != null) {
      fields.put(OpsLogFields.OUTPUT_CHAR_COUNT, result.output().text().length());
    }
    putDuration(fields, timing, now());
    opsLogger.info(log, OpsLogEventType.AGENT_RUN_COMPLETED, fields);
  }

  @Override
  public void onError(AgentLoopContext context, AgentException error) {
    AgentOpsSource agentSource = source(context);
    RunTiming timing = runTimings.remove(context.runId());
    removeToolTimings(context.runId());
    agent.runFailed(agentSource);
    Map<String, Object> fields = baseFields(context, agentSource);
    fields.put(OpsLogFields.ERROR_CODE, error.code().name());
    fields.put(OpsLogFields.EXCEPTION_TYPE, error.getClass().getSimpleName());
    putDuration(fields, timing, now());
    opsLogger.warn(
        log,
        OpsLogEventType.AGENT_RUN_FAILED,
        fields,
        null);
  }

  @Override
  public void onToolStart(AgentLoopContext context, int stepIndex, LlmToolCall toolCall) {
    toolStartNanos.put(new ToolCallKey(context.runId(), stepIndex, toolCall.id()), now());
    Map<String, Object> fields = toolFields(context, stepIndex, toolCall);
    fields.put(OpsLogFields.TOOL_ARGUMENTS, summarizeToolArguments(toolCall.arguments()));
    opsLogger.info(log, OpsLogEventType.AGENT_TOOL_STARTED, fields);
  }

  @Override
  public void onToolEnd(AgentLoopContext context, int stepIndex, LlmToolCall toolCall, JsonNode result) {
    agent.toolExecution(toolCall.name(), OpsStatus.COMPLETED);
    Map<String, Object> fields = toolFields(context, stepIndex, toolCall);
    putToolDuration(fields, context, stepIndex, toolCall, now());
    fields.put(OpsLogFields.TOOL_RESULT, summarizeToolResult(result));
    opsLogger.info(log, OpsLogEventType.AGENT_TOOL_COMPLETED, fields);
  }

  @Override
  public void onToolError(AgentLoopContext context, int stepIndex, LlmToolCall toolCall, AgentException error) {
    agent.toolExecution(toolCall.name(), OpsStatus.FAILED);
    Map<String, Object> fields = toolFields(context, stepIndex, toolCall);
    fields.put(OpsLogFields.ERROR_CODE, error.code().name());
    fields.put(OpsLogFields.EXCEPTION_TYPE, error.getClass().getSimpleName());
    putToolDuration(fields, context, stepIndex, toolCall, now());
    opsLogger.warn(log, OpsLogEventType.AGENT_TOOL_FAILED, fields, null);
  }

  @Override
  public void onToolPermissionDecision(
      AgentLoopContext context,
      AgentToolPermissionRequest request,
      AgentToolPermissionDecision decision,
      AgentToolPermissionDecisionPlan plan) {
    agent.toolPermissionDecision(decision.decision().name());
  }

  @Override
  public void onToolPermissionTimeout(
      AgentLoopContext context,
      AgentToolPermissionRequest request,
      String reason,
      Instant expiredAt,
      AgentToolPermissionDecisionPlan plan) {
    agent.toolPermissionDecision(PERMISSION_TIMEOUT_DECISION);
    opsLogger.warn(
        log,
        OpsLogEventType.AGENT_TOOL_PERMISSION_TIMEOUT,
        Map.of(OpsLogFields.TOOL_NAME, request.toolName()),
        null);
  }

  AgentOpsSource source(AgentLoopContext context) {
    if (context == null) {
      return FALLBACK_SOURCE;
    }

    return AgentOpsSource.fromAgentKey(context.metadata().get(AgentRuntimeMetadataKeys.AGENT_KEY));
  }

  private StepTiming timing(AgentLoopContext context, int stepIndex) {
    RunTiming runTiming = runTimings.computeIfAbsent(context.runId(), ignored -> new RunTiming(now()));
    return runTiming.stepTimings.computeIfAbsent(stepIndex, ignored -> new StepTiming());
  }

  private long now() {
    return nanoTime.getAsLong();
  }

  private Map<String, Object> baseFields(AgentLoopContext context, AgentOpsSource agentSource) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put(OpsLogFields.AGENT_RUN_ID, context.runId());
    fields.put(OpsLogFields.AGENT_SOURCE, agentSource.tagValue());
    Object agentKey = context.metadata().get(AgentRuntimeMetadataKeys.AGENT_KEY);
    if (agentKey instanceof String value && !value.isBlank()) {
      fields.put(OpsLogFields.AGENT_KEY, value);
    }
    return fields;
  }

  private Map<String, Object> toolFields(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall
  ) {
    Map<String, Object> fields = baseFields(context, source(context));
    fields.put(OpsLogFields.STEP_INDEX, stepIndex);
    fields.put(OpsLogFields.TOOL_NAME, toolCall.name());
    fields.put(OpsLogFields.TOOL_CALL_ID, toolCall.id());
    return fields;
  }

  private void putDuration(Map<String, Object> fields, RunTiming timing, long endedAtNanos) {
    if (timing != null) {
      fields.put(OpsLogFields.DURATION_MS, elapsedMillis(timing.startedAtNanos, endedAtNanos));
    }
  }

  private void putToolDuration(
      Map<String, Object> fields,
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      long endedAtNanos
  ) {
    Long startedAtNanos = toolStartNanos.remove(new ToolCallKey(context.runId(), stepIndex, toolCall.id()));
    if (startedAtNanos != null) {
      fields.put(OpsLogFields.DURATION_MS, elapsedMillis(startedAtNanos, endedAtNanos));
    }
  }

  private void removeToolTimings(String runId) {
    toolStartNanos.keySet().removeIf(key -> key.runId.equals(runId));
  }

  private long elapsedMillis(long startedAtNanos, long endedAtNanos) {
    if (startedAtNanos == NOT_RECORDED || endedAtNanos < startedAtNanos) {
      return 0L;
    }
    return TimeUnit.NANOSECONDS.toMillis(endedAtNanos - startedAtNanos);
  }

  private void putUsage(Map<String, Object> fields, LlmUsage usage) {
    if (usage == null) {
      return;
    }
    fields.put(OpsLogFields.INPUT_TOKENS, usage.inputTokens());
    fields.put(OpsLogFields.OUTPUT_TOKENS, usage.outputTokens());
    fields.put(OpsLogFields.CACHED_TOKENS, usage.cachedTokens());
    fields.put(OpsLogFields.REASONING_TOKENS, usage.reasoningTokens());
    fields.put(OpsLogFields.TOTAL_TOKENS, usage.totalTokens());
  }

  private void putIfNonBlank(Map<String, Object> fields, String fieldName, String value) {
    if (value != null && !value.isBlank()) {
      fields.put(fieldName, value);
    }
  }

  private Map<String, Object> summarizeToolArguments(JsonNode arguments) {
    if (arguments == null || !arguments.isObject()) {
      return Map.of();
    }
    Map<String, Object> summary = new LinkedHashMap<>();
    SAFE_TOOL_ARGUMENT_FIELDS.stream()
        .filter(arguments::hasNonNull)
        .sorted()
        .forEach(fieldName -> summary.put(fieldName, safeScalar(arguments.get(fieldName))));
    addPresence(summary, arguments, "keyword", "keywordProvided");
    addPresence(summary, arguments, "resultRef", "resultRefProvided");
    return Collections.unmodifiableMap(summary);
  }

  private void addPresence(
      Map<String, Object> summary,
      JsonNode arguments,
      String sourceField,
      String summaryField
  ) {
    if (arguments.hasNonNull(sourceField)) {
      summary.put(summaryField, true);
    }
  }

  private Object safeScalar(JsonNode value) {
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isBoolean()) {
      return value.booleanValue();
    }
    if (value.isIntegralNumber()) {
      return value.longValue();
    }
    if (value.isFloatingPointNumber()) {
      return value.doubleValue();
    }
    if (value.isTextual()) {
      return value.asText();
    }
    return value.getNodeType().name();
  }

  private Map<String, Object> summarizeToolResult(JsonNode result) {
    if (result == null || result.isNull()) {
      return Map.of("resultType", "null");
    }
    if (!result.isObject()) {
      return Map.of("resultType", result.getNodeType().name());
    }
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("resultKeys", topLevelKeys(result));
    copyText(result, summary, "type");
    SAFE_TOOL_RESULT_NUMBER_FIELDS.stream()
        .filter(result::hasNonNull)
        .sorted()
        .forEach(fieldName -> summary.put(fieldName, safeScalar(result.get(fieldName))));
    SAFE_TOOL_RESULT_BOOLEAN_FIELDS.stream()
        .filter(result::hasNonNull)
        .sorted()
        .forEach(fieldName -> summary.put(fieldName, safeScalar(result.get(fieldName))));
    JsonNode items = result.get("items");
    if (items != null && items.isArray()) {
      summary.put("itemCount", items.size());
    }
    Map<String, Integer> arrayCounts = topLevelArrayCounts(result);
    if (!arrayCounts.isEmpty()) {
      summary.put("arrayCounts", arrayCounts);
    }
    return Collections.unmodifiableMap(summary);
  }

  private ArrayList<String> topLevelKeys(JsonNode result) {
    ArrayList<String> keys = new ArrayList<>();
    result.fieldNames().forEachRemaining(keys::add);
    keys.sort(Comparator.naturalOrder());
    return keys;
  }

  private void copyText(JsonNode result, Map<String, Object> summary, String fieldName) {
    JsonNode value = result.get(fieldName);
    if (value != null && value.isTextual()) {
      summary.put(fieldName, value.asText());
    }
  }

  private Map<String, Integer> topLevelArrayCounts(JsonNode result) {
    Map<String, Integer> counts = new TreeMap<>();
    result.properties().forEach(entry -> {
      if (!"items".equals(entry.getKey()) && entry.getValue().isArray()) {
        counts.put(entry.getKey(), entry.getValue().size());
      }
    });
    return Collections.unmodifiableMap(counts);
  }

  private static final class RunTiming {
    private final long startedAtNanos;
    private final ConcurrentMap<Integer, StepTiming> stepTimings = new ConcurrentHashMap<>();

    private RunTiming(long startedAtNanos) {
      this.startedAtNanos = startedAtNanos;
    }
  }

  private static final class StepTiming {
    private volatile long stepStartedNanos = NOT_RECORDED;
    private volatile long llmRequestStartedNanos = NOT_RECORDED;
    private final AtomicLong firstEventNanos = new AtomicLong(NOT_RECORDED);
    private volatile String provider;
    private volatile String model;
    private volatile LlmUsage usage;

    private void markStepStarted(long startedAtNanos) {
      if (stepStartedNanos == NOT_RECORDED) {
        stepStartedNanos = startedAtNanos;
      }
    }

    private void markLlmRequestStarted(long startedAtNanos) {
      markStepStarted(startedAtNanos);
      llmRequestStartedNanos = startedAtNanos;
    }

    private void recordFirstEvent(long eventAtNanos) {
      firstEventNanos.compareAndSet(NOT_RECORDED, eventAtNanos);
    }
  }

  private record ToolCallKey(String runId, int stepIndex, String toolCallId) {
  }

}
