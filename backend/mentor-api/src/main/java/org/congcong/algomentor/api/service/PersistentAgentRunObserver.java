package org.congcong.algomentor.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.springframework.jdbc.core.JdbcOperations;

public class PersistentAgentRunObserver implements AgentLoopObserver {

  private final JdbcOperations jdbcOperations;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final Map<String, RunBuffer> buffers = new ConcurrentHashMap<>();

  public PersistentAgentRunObserver(JdbcOperations jdbcOperations, ObjectMapper objectMapper) {
    this(jdbcOperations, objectMapper, Clock.systemUTC());
  }

  PersistentAgentRunObserver(JdbcOperations jdbcOperations, ObjectMapper objectMapper, Clock clock) {
    this.jdbcOperations = jdbcOperations;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  public void onRunStart(AgentLoopContext context) {
    if (runDbId(context) == null) {
      return;
    }
    buffers.put(context.runId(), new RunBuffer());
    jdbcOperations.update("""
        UPDATE agent_run
        SET status = 'running', started_at = COALESCE(started_at, ?), max_steps = ?
        WHERE id = ?
        """, Timestamp.from(clock.instant()), context.maxSteps(), runDbId(context));
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
    Long taskId = longMetadata(context, "taskId");
    Long turnId = longMetadata(context, "turnId");
    Long runDbId = runDbId(context);
    if (taskId == null || turnId == null || runDbId == null) {
      return;
    }
    RunBuffer buffer = buffers.remove(context.runId());
    String content = buffer == null ? "" : buffer.content.toString();
    Long assistantMessageId = content.isBlank() ? null : insertAssistantMessage(taskId, turnId, runDbId, content);
    jdbcOperations.update("""
        UPDATE agent_run
        SET status = 'succeeded',
            provider = ?,
            model = ?,
            finish_reason = ?,
            usage = ?::jsonb,
            ended_at = ?
        WHERE id = ?
        """,
        buffer == null ? null : buffer.provider,
        buffer == null ? null : buffer.model,
        result.finishReason().name(),
        toJson(usageMap(buffer == null ? null : buffer.usage)),
        Timestamp.from(clock.instant()),
        runDbId);
    jdbcOperations.update("""
        UPDATE agent_turn
        SET status = 'succeeded',
            assistant_message_id = COALESCE(?, assistant_message_id),
            accepted_run_id = ?,
            current_run_id = ?,
            updated_at = ?
        WHERE id = ?
        """,
        assistantMessageId,
        runDbId,
        runDbId,
        Timestamp.from(clock.instant()),
        turnId);
  }

  @Override
  public void onError(AgentLoopContext context, AgentException error) {
    Long runDbId = runDbId(context);
    if (runDbId == null) {
      return;
    }
    buffers.remove(context.runId());
    jdbcOperations.update("""
        UPDATE agent_run
        SET status = 'failed',
            error = ?::jsonb,
            ended_at = ?
        WHERE id = ?
        """,
        toJson(Map.of(
            "code", error.code().name(),
            "message", error.getMessage(),
            "retryable", error.retryable(),
            "metadata", error.metadata())),
        Timestamp.from(clock.instant()),
        runDbId);
    Long turnId = longMetadata(context, "turnId");
    if (turnId != null) {
      jdbcOperations.update("""
          UPDATE agent_turn
          SET status = 'failed', updated_at = ?
          WHERE id = ?
          """, Timestamp.from(clock.instant()), turnId);
    }
  }

  private Long insertAssistantMessage(long taskId, long turnId, long runId, String content) {
    Long sequenceNo = jdbcOperations.queryForObject(
        "SELECT COALESCE(MAX(sequence_no), 0) + 1 FROM agent_message WHERE task_id = ?",
        Long.class,
        taskId);
    return jdbcOperations.queryForObject("""
        INSERT INTO agent_message (task_id, turn_id, run_id, role, content, sequence_no, status, token_estimate, created_at, updated_at)
        VALUES (?, ?, ?, 'assistant', ?, ?, 'active', ?, ?, ?)
        RETURNING id
        """,
        Long.class,
        taskId,
        turnId,
        runId,
        content,
        sequenceNo,
        Math.max(1, content.length() / 4),
        Timestamp.from(clock.instant()),
        Timestamp.from(clock.instant()));
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

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Failed to serialize agent run JSON", ex);
    }
  }

  private Long runDbId(AgentLoopContext context) {
    return longMetadata(context, "runDbId");
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

  private static final class RunBuffer {
    private final StringBuilder content = new StringBuilder();
    private LlmUsage usage;
    private String provider;
    private String model;
  }
}
