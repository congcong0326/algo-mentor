package org.congcong.algomentor.api.service;

import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class LlmStreamSseMapper {

  public SseEmitter.SseEventBuilder toSseEvent(AgentStreamEvent event) {
    return SseEmitter.event().name(event.name()).data(toData(event));
  }

  /** 将稳定 SSE 协议的 data 字段映射为可经 Redis envelope 持久化的 JSON 值。 */
  public Object toData(AgentStreamEvent event) {
    if (event instanceof AgentStreamEvent.AgentRunStart start) {
      return new AgentRunStartData(
          start.runId(),
          start.topic(),
          start.maxSteps(),
          longValue(start.metadata(), AgentRuntimeMetadataKeys.TASK_ID),
          longValue(start.metadata(), AgentRuntimeMetadataKeys.TURN_ID),
          longValue(start.metadata(), AgentRuntimeMetadataKeys.RUN_DB_ID),
          start.metadata());
    }
    if (event instanceof AgentStreamEvent.AgentStepStart start) {
      return new AgentStepStartData(start.runId(), start.stepIndex());
    }
    if (event instanceof AgentStreamEvent.AgentToolStart start) {
      return new AgentToolStartData(start.runId(), start.stepIndex(), start.toolCallId(), start.toolName());
    }
    if (event instanceof AgentStreamEvent.AgentToolEnd end) {
      return new AgentToolEndData(end.runId(), end.stepIndex(), end.toolCallId(), end.toolName(), end.result());
    }
    if (event instanceof AgentStreamEvent.ToolPermissionRequest request) {
      return new ToolPermissionRequestData(
              request.runId(),
              request.stepIndex(),
              request.toolCallId(),
              request.toolName(),
              request.permissionRequestId(),
              request.displayName(),
              request.reason(),
              request.copyCode(),
              request.preview(),
              request.expiresAt());
    }
    if (event instanceof AgentStreamEvent.ToolPermissionDecision decision) {
      return new ToolPermissionDecisionData(
              decision.runId(),
              decision.stepIndex(),
              decision.toolCallId(),
              decision.toolName(),
              decision.permissionRequestId(),
              decision.decision(),
              decision.reason(),
              decision.decidedAt());
    }
    if (event instanceof AgentStreamEvent.ToolPermissionTimeout timeout) {
      return new ToolPermissionTimeoutData(
              timeout.runId(),
              timeout.stepIndex(),
              timeout.toolCallId(),
              timeout.toolName(),
              timeout.permissionRequestId(),
              timeout.reason(),
              timeout.expiredAt());
    }
    if (event instanceof AgentStreamEvent.AgentStepEnd end) {
      return new AgentStepEndData(end.runId(), end.stepIndex(), end.finishReason(), end.toolCallCount());
    }
    if (event instanceof AgentStreamEvent.AgentRunEnd end) {
      return new AgentRunEndData(end.runId(), end.steps(), end.finishReason(), end.metadata());
    }
    if (event instanceof AgentStreamEvent.AgentError error) {
      return AgentErrorData.from(error.error());
    }
    if (event instanceof AgentStreamEvent.Llm llm) {
      return toLlmData(llm.event());
    }
    throw new IllegalArgumentException("Unsupported agent stream event: " + event.getClass().getName());
  }

  private Object toLlmData(LlmStreamEvent event) {
    if (event instanceof LlmStreamEvent.MessageStart start) {
      return new MessageStartData(start.provider().value(), start.model().value());
    }
    if (event instanceof LlmStreamEvent.ContentDelta delta) {
      return new ContentDeltaData(delta.content());
    }
    if (event instanceof LlmStreamEvent.ToolCallStart start) {
      return new ToolCallStartData(start.id(), start.name());
    }
    if (event instanceof LlmStreamEvent.ToolCallDelta delta) {
      return new ToolCallDeltaData(delta.id(), delta.argumentsDelta());
    }
    if (event instanceof LlmStreamEvent.ToolCallEnd end) {
      return new ToolCallEndData(end.toolCall());
    }
    if (event instanceof LlmStreamEvent.Usage usage) {
      return new UsageData(usage.usage());
    }
    if (event instanceof LlmStreamEvent.MessageEnd end) {
      return new MessageEndData(end.finishReason(), end.metadata());
    }
    if (event instanceof LlmStreamEvent.Error error) {
      return ErrorData.from(error.error());
    }
    if (event instanceof LlmStreamEvent.Heartbeat) {
      return Map.of();
    }
    throw new IllegalArgumentException("Unsupported LLM stream event: " + event.getClass().getName());
  }

  private record MessageStartData(String provider, String model) {
  }

  private record ContentDeltaData(String content) {
  }

  private Long longValue(Map<String, Object> metadata, String key) {
    if (metadata == null) {
      return null;
    }
    Object value = metadata.get(key);
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      return Long.parseLong(text);
    }
    return null;
  }

  private record AgentRunStartData(
      String runId,
      String topic,
      int maxSteps,
      Long taskId,
      Long turnId,
      Long runDbId,
      Map<String, Object> metadata
  ) {
  }

  private record AgentStepStartData(String runId, int stepIndex) {
  }

  private record AgentToolStartData(String runId, int stepIndex, String toolCallId, String toolName) {
  }

  private record AgentToolEndData(
      String runId,
      int stepIndex,
      String toolCallId,
      String toolName,
      com.fasterxml.jackson.databind.JsonNode result
  ) {
  }

  private record ToolPermissionRequestData(
      String runId,
      int stepIndex,
      String toolCallId,
      String toolName,
      String permissionRequestId,
      String displayName,
      String reason,
      String copyCode,
      Map<String, Object> preview,
      Instant expiresAt
  ) {
  }

  private record ToolPermissionDecisionData(
      String runId,
      int stepIndex,
      String toolCallId,
      String toolName,
      String permissionRequestId,
      AgentToolPermissionDecisionType decision,
      String reason,
      Instant decidedAt
  ) {
  }

  private record ToolPermissionTimeoutData(
      String runId,
      int stepIndex,
      String toolCallId,
      String toolName,
      String permissionRequestId,
      String reason,
      Instant expiredAt
  ) {
  }

  private record AgentStepEndData(String runId, int stepIndex, LlmFinishReason finishReason, int toolCallCount) {
  }

  private record AgentRunEndData(String runId, int steps, LlmFinishReason finishReason, Map<String, Object> metadata) {
  }

  private record ToolCallStartData(String id, String name) {
  }

  private record ToolCallDeltaData(String id, String argumentsDelta) {
  }

  private record ToolCallEndData(LlmToolCall toolCall) {
  }

  private record UsageData(LlmUsage usage) {
  }

  private record MessageEndData(LlmFinishReason finishReason, Map<String, Object> metadata) {
  }

  private record ErrorData(
      String code,
      String message,
      boolean retryable,
      String provider,
      String model,
      Map<String, Object> metadata
  ) {

    private static ErrorData from(LlmException error) {
      return new ErrorData(
          error.code().name(),
          error.getMessage(),
          error.retryable(),
          error.provider() == null ? null : error.provider().value(),
          error.model() == null ? null : error.model().value(),
          error.metadata());
    }
  }

  private record AgentErrorData(
      String code,
      String message,
      boolean retryable,
      Map<String, Object> metadata
  ) {

    private static AgentErrorData from(AgentException error) {
      return new AgentErrorData(
          error.code().name(),
          error.getMessage(),
          error.retryable(),
          error.metadata());
    }
  }
}
