package org.congcong.algomentor.llm.core;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface LlmStreamEvent
    permits LlmStreamEvent.MessageStart,
    LlmStreamEvent.ContentDelta,
    LlmStreamEvent.ToolCallStart,
    LlmStreamEvent.ToolCallDelta,
    LlmStreamEvent.ToolCallEnd,
    LlmStreamEvent.MessageEnd,
    LlmStreamEvent.Usage,
    LlmStreamEvent.Error,
    LlmStreamEvent.Heartbeat {

  record MessageStart(LlmProviderId provider, LlmModelId model) implements LlmStreamEvent {
  }

  record ContentDelta(String text) implements LlmStreamEvent {
  }

  record ToolCallStart(String id, String name) implements LlmStreamEvent {
  }

  record ToolCallDelta(String id, JsonNode argumentsDelta) implements LlmStreamEvent {
  }

  record ToolCallEnd(LlmToolCall toolCall) implements LlmStreamEvent {
  }

  record MessageEnd(LlmFinishReason finishReason) implements LlmStreamEvent {
  }

  record Usage(LlmUsage usage) implements LlmStreamEvent {
  }

  record Error(LlmErrorCode code, String message) implements LlmStreamEvent {
  }

  record Heartbeat() implements LlmStreamEvent {
  }
}
