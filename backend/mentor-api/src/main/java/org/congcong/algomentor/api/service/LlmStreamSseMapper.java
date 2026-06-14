package org.congcong.algomentor.api.service;

import java.util.Map;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
class LlmStreamSseMapper {

  SseEmitter.SseEventBuilder toSseEvent(LlmStreamEvent event) {
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
}
