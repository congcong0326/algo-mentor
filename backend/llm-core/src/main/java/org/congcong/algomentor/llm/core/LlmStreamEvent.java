package org.congcong.algomentor.llm.core;

import java.util.Map;

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

  record ContentDelta(String content) implements LlmStreamEvent {
    public ContentDelta {
      if (content == null) {
        throw new IllegalArgumentException("LLM stream content delta must not be null");
      }
    }
  }

  record ToolCallStart(String id, String name) implements LlmStreamEvent {
    public ToolCallStart {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("LLM stream tool call id must not be blank");
      }
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("LLM stream tool call name must not be blank");
      }
    }
  }

  record ToolCallDelta(String id, String argumentsDelta) implements LlmStreamEvent {
    public ToolCallDelta {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("LLM stream tool call id must not be blank");
      }
      if (argumentsDelta == null) {
        throw new IllegalArgumentException("LLM stream tool call arguments delta must not be null");
      }
    }
  }

  record ToolCallEnd(LlmToolCall toolCall) implements LlmStreamEvent {
    public ToolCallEnd {
      if (toolCall == null) {
        throw new IllegalArgumentException("LLM stream tool call must not be null");
      }
    }
  }

  record MessageEnd(LlmFinishReason finishReason, Map<String, Object> metadata) implements LlmStreamEvent {
    public MessageEnd {
      finishReason = finishReason == null ? LlmFinishReason.UNKNOWN : finishReason;
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }

  record Usage(LlmUsage usage) implements LlmStreamEvent {
    public Usage {
      if (usage == null) {
        throw new IllegalArgumentException("LLM stream usage must not be null");
      }
    }
  }

  record Error(LlmException error) implements LlmStreamEvent {
    public Error {
      if (error == null) {
        throw new IllegalArgumentException("LLM stream error must not be null");
      }
    }
  }

  record Heartbeat() implements LlmStreamEvent {
  }
}
