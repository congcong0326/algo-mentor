package org.congcong.algomentor.api.service;

import java.util.Map;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
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
    if (event instanceof AgentStreamEvent.AgentRunStart start) {
      return event("agent_run_start", new AgentRunStartData(start.runId(), start.topic(), start.maxSteps()));
    }
    if (event instanceof AgentStreamEvent.AgentStepStart start) {
      return event("agent_step_start", new AgentStepStartData(start.runId(), start.stepIndex()));
    }
    if (event instanceof AgentStreamEvent.AgentToolStart start) {
      return event(
          "agent_tool_start",
          new AgentToolStartData(start.runId(), start.stepIndex(), start.toolCallId(), start.toolName()));
    }
    if (event instanceof AgentStreamEvent.AgentToolEnd end) {
      return event(
          "agent_tool_end",
          new AgentToolEndData(end.runId(), end.stepIndex(), end.toolCallId(), end.toolName(), end.result()));
    }
    if (event instanceof AgentStreamEvent.AgentStepEnd end) {
      return event(
          "agent_step_end",
          new AgentStepEndData(end.runId(), end.stepIndex(), end.finishReason(), end.toolCallCount()));
    }
    if (event instanceof AgentStreamEvent.AgentRunEnd end) {
      return event("agent_run_end", new AgentRunEndData(end.runId(), end.steps(), end.finishReason(), end.metadata()));
    }
    if (event instanceof AgentStreamEvent.AgentError error) {
      return event("agent_error", AgentErrorData.from(error.error()));
    }
    if (event instanceof AgentStreamEvent.Llm llm) {
      return toLlmSseEvent(llm.event());
    }
    throw new IllegalArgumentException("Unsupported agent stream event: " + event.getClass().getName());
  }

  private SseEmitter.SseEventBuilder toLlmSseEvent(LlmStreamEvent event) {
    if (event instanceof LlmStreamEvent.MessageStart start) {
      return event("message_start", new MessageStartData(start.provider().value(), start.model().value()));
    }
    if (event instanceof LlmStreamEvent.ContentDelta delta) {
      return event("content_delta", new ContentDeltaData(delta.content()));
    }
    if (event instanceof LlmStreamEvent.ToolCallStart start) {
      return event("tool_call_start", new ToolCallStartData(start.id(), start.name()));
    }
    if (event instanceof LlmStreamEvent.ToolCallDelta delta) {
      return event("tool_call_delta", new ToolCallDeltaData(delta.id(), delta.argumentsDelta()));
    }
    if (event instanceof LlmStreamEvent.ToolCallEnd end) {
      return event("tool_call_end", new ToolCallEndData(end.toolCall()));
    }
    if (event instanceof LlmStreamEvent.Usage usage) {
      return event("usage", new UsageData(usage.usage()));
    }
    if (event instanceof LlmStreamEvent.MessageEnd end) {
      return event("message_end", new MessageEndData(end.finishReason(), end.metadata()));
    }
    if (event instanceof LlmStreamEvent.Error error) {
      return event("error", ErrorData.from(error.error()));
    }
    if (event instanceof LlmStreamEvent.Heartbeat) {
      return event("heartbeat", Map.of());
    }
    throw new IllegalArgumentException("Unsupported LLM stream event: " + event.getClass().getName());
  }

  private SseEmitter.SseEventBuilder event(String name, Object data) {
    return SseEmitter.event().name(name).data(data);
  }

  private record MessageStartData(String provider, String model) {
  }

  private record ContentDeltaData(String content) {
  }

  private record AgentRunStartData(String runId, String topic, int maxSteps) {
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
