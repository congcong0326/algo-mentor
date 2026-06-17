package org.congcong.algomentor.agent.persistence.postgres.observer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStartUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunSuccessUpdate;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

public class PersistentAgentRunObserver implements AgentLoopObserver {

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
    if (event instanceof LlmStreamEvent.ContentDelta delta) {
      buffer.content.append(delta.content());
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
  public void onRunEnd(AgentLoopContext context, AgentRunResult result) {
    Long taskId = longMetadata(context, AgentRuntimeMetadataKeys.TASK_ID);
    Long turnId = longMetadata(context, AgentRuntimeMetadataKeys.TURN_ID);
    Long runDbId = runDbId(context);
    if (taskId == null || turnId == null || runDbId == null) {
      return;
    }

    RunBuffer buffer = buffers.remove(context.runId());
    Instant now = clock.instant();
    String content = buffer == null ? "" : buffer.content.toString();
    Long assistantMessageId = content.isBlank()
        ? null
        : runMapper.insertAssistantMessage(
            taskId,
            turnId,
            runDbId,
            content,
            estimateTokens(content),
            now,
            now);

    runMapper.markRunSucceeded(new RunSuccessUpdate(
        runDbId,
        buffer == null ? null : buffer.provider,
        buffer == null ? null : buffer.model,
        result.finishReason().name(),
        jsonNode(usageMap(buffer == null ? null : buffer.usage)),
        now));
    runMapper.markTurnSucceeded(turnId, assistantMessageId, runDbId, now);
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
        jsonNode(Map.of(
            "code", error.code().name(),
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
    values.put("inputTokens", usage.inputTokens());
    values.put("outputTokens", usage.outputTokens());
    values.put("cachedTokens", usage.cachedTokens());
    values.put("reasoningTokens", usage.reasoningTokens());
    values.put("totalTokens", usage.totalTokens());
    return values;
  }

  private JsonNode jsonNode(Object value) {
    return objectMapper.valueToTree(value);
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
    private final StringBuilder content = new StringBuilder();
    private LlmUsage usage;
    private String provider;
    private String model;
  }
}
