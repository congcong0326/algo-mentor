package org.congcong.algomentor.agent.persistence.postgres.observer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentOutput;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.persistence.postgres.AgentPersistenceStatuses;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStartUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunSuccessUpdate;
import org.congcong.algomentor.llm.core.metadata.LlmMetadataKeys;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

public class PersistentAgentRunObserver implements AgentLoopObserver {

  private static final String SCENARIO = "scenario";
  private static final String PRACTICE_CHAT_SCENARIO = "PRACTICE_CHAT";
  private static final String MESSAGE_TYPE = "messageType";
  private static final String PRACTICE_SESSION_ID = "practiceSessionId";
  private static final String PLAN_ID = "planId";
  private static final String PHASE_INDEX = "phaseIndex";
  private static final String PROBLEM_SLUG = "problemSlug";

  private final AgentRunMapper runMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Map<String, RunBuffer> buffers = new ConcurrentHashMap<>();

  public PersistentAgentRunObserver(AgentRunMapper runMapper, ObjectMapper objectMapper) {
    this(runMapper, objectMapper, Clock.systemUTC());
  }

  public PersistentAgentRunObserver(AgentRunMapper runMapper, ObjectMapper objectMapper, Clock clock) {
    this.runMapper = runMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  public void onRunStart(AgentLoopContext context) {
    Long runDbId = runDbId(context);
    if (runDbId == null) {
      return;
    }
    buffers.put(context.runId(), new RunBuffer());
    runMapper.markRunStarted(new RunStartUpdate(runDbId, context.maxSteps(), clock.instant()));
  }

  @Override
  public void onLlmEvent(AgentLoopContext context, int stepIndex, LlmStreamEvent event) {
    RunBuffer buffer = buffers.get(context.runId());
    if (buffer == null) {
      return;
    }
    if (event instanceof LlmStreamEvent.Usage usage) {
      buffer.usage = usage.usage();
    }
    if (event instanceof LlmStreamEvent.MessageStart start) {
      buffer.provider = start.provider() == null ? null : start.provider().value();
      buffer.model = start.model() == null ? null : start.model().value();
    }
  }

  @Override
  public void onFinalOutput(AgentLoopContext context, AgentOutput output) {
    Long taskId = longMetadata(context, AgentRuntimeMetadataKeys.TASK_ID);
    Long turnId = longMetadata(context, AgentRuntimeMetadataKeys.TURN_ID);
    Long runDbId = runDbId(context);
    RunBuffer buffer = buffers.get(context.runId());
    if (taskId == null || turnId == null || runDbId == null || buffer == null || output == null) {
      return;
    }
    String content = output.text();
    if (content.isBlank()) {
      return;
    }
    Instant now = clock.instant();
    buffer.assistantMessageId = runMapper.insertAssistantMessage(
        taskId,
        turnId,
        runDbId,
        content,
        estimateTokens(content),
        assistantMessageMetadata(context),
        now,
        now);
  }

  @Override
  public void onRunEnd(AgentLoopContext context, AgentRunResult result) {
    Long taskId = longMetadata(context, AgentRuntimeMetadataKeys.TASK_ID);
    Long turnId = longMetadata(context, AgentRuntimeMetadataKeys.TURN_ID);
    Long runDbId = runDbId(context);
    if (taskId == null || turnId == null || runDbId == null) {
      return;
    }

    RunBuffer buffer = buffers.remove(context.runId());
    Instant now = clock.instant();

    runMapper.markRunSucceeded(new RunSuccessUpdate(
        runDbId,
        buffer == null ? null : buffer.provider,
        buffer == null ? null : buffer.model,
        result.finishReason().name(),
        jsonNode(usageMap(buffer == null ? null : buffer.usage)),
        now));
    runMapper.markTurnSucceeded(turnId, buffer == null ? null : buffer.assistantMessageId, runDbId, now);
  }

  @Override
  public void onError(AgentLoopContext context, AgentException error) {
    Long runDbId = runDbId(context);
    if (runDbId == null) {
      return;
    }
    buffers.remove(context.runId());
    Instant now = clock.instant();
    runMapper.markRunFailed(new RunErrorUpdate(
        runDbId,
        error.code() == AgentErrorCode.CANCELLED
            ? AgentPersistenceStatuses.CANCELLED
            : AgentPersistenceStatuses.FAILED,
        jsonNode(Map.of(
            LlmMetadataKeys.CODE, error.code().name(),
            "message", error.getMessage(),
            "retryable", error.retryable(),
            "metadata", error.metadata())),
        now));
    Long turnId = longMetadata(context, AgentRuntimeMetadataKeys.TURN_ID);
    if (turnId != null) {
      runMapper.markTurnFailed(turnId, now);
    }
  }

  private Map<String, Object> usageMap(LlmUsage usage) {
    if (usage == null) {
      return Map.of();
    }
    Map<String, Object> values = new HashMap<>();
    values.put(LlmMetadataKeys.INPUT_TOKENS, usage.inputTokens());
    values.put(LlmMetadataKeys.OUTPUT_TOKENS, usage.outputTokens());
    values.put(LlmMetadataKeys.CACHED_TOKENS, usage.cachedTokens());
    values.put(LlmMetadataKeys.REASONING_TOKENS, usage.reasoningTokens());
    values.put(LlmMetadataKeys.TOTAL_TOKENS, usage.totalTokens());
    return values;
  }

  private JsonNode jsonNode(Object value) {
    return objectMapper.valueToTree(value);
  }

  private Map<String, Object> assistantMessageMetadata(AgentLoopContext context) {
    Object scenario = context.metadata().get(SCENARIO);
    Object messageType = context.metadata().get(MESSAGE_TYPE);
    if (!PRACTICE_CHAT_SCENARIO.equals(scenario) || messageType == null) {
      return Map.of();
    }
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(MESSAGE_TYPE, messageType);
    copyMetadata(context, metadata, SCENARIO);
    copyMetadata(context, metadata, PRACTICE_SESSION_ID);
    copyMetadata(context, metadata, PLAN_ID);
    copyMetadata(context, metadata, PHASE_INDEX);
    copyMetadata(context, metadata, PROBLEM_SLUG);
    return Map.copyOf(metadata);
  }

  private void copyMetadata(AgentLoopContext context, Map<String, Object> target, String key) {
    Object value = context.metadata().get(key);
    if (value != null) {
      target.put(key, value);
    }
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

  private int estimateTokens(String content) {
    return Math.max(1, content.length() / 4);
  }

  private static final class RunBuffer {
    private LlmUsage usage;
    private String provider;
    private String model;
    private Long assistantMessageId;
  }
}
