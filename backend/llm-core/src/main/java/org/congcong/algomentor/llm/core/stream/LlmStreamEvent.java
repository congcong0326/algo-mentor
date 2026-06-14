package org.congcong.algomentor.llm.core.stream;

import java.util.Map;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

/**
 * Events emitted by streaming LLM calls from message start through deltas, usage, errors, and completion.
 */
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

  /**
   * Marks the start of a streamed model response.
   */
  record MessageStart(LlmProviderId provider, LlmModelId model) implements LlmStreamEvent {
  }

  /**
   * Carries an incremental text content delta.
   */
  record ContentDelta(String content) implements LlmStreamEvent {
    public ContentDelta {
      if (content == null) {
        throw new IllegalArgumentException("LLM stream content delta must not be null");
      }
    }
  }

  /**
   * Announces a tool call being streamed.
   */
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

  /**
   * Carries an incremental tool arguments delta.
   */
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

  /**
   * Marks completion of a streamed tool call.
   */
  record ToolCallEnd(LlmToolCall toolCall) implements LlmStreamEvent {
    public ToolCallEnd {
      if (toolCall == null) {
        throw new IllegalArgumentException("LLM stream tool call must not be null");
      }
    }
  }

  /**
   * Marks the end of a streamed model response.
   */
  record MessageEnd(LlmFinishReason finishReason, Map<String, Object> metadata) implements LlmStreamEvent {
    public MessageEnd {
      finishReason = finishReason == null ? LlmFinishReason.UNKNOWN : finishReason;
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
  }

  /**
   * Reports token usage during or after a stream.
   */
  record Usage(LlmUsage usage) implements LlmStreamEvent {
    public Usage {
      if (usage == null) {
        throw new IllegalArgumentException("LLM stream usage must not be null");
      }
    }
  }

  /**
   * Reports a provider or gateway error during streaming.
   */
  record Error(LlmException error) implements LlmStreamEvent {
    public Error {
      if (error == null) {
        throw new IllegalArgumentException("LLM stream error must not be null");
      }
    }
  }

  /**
   * Keeps a streaming connection alive when no content is ready.
   */
  record Heartbeat() implements LlmStreamEvent {
  }
}
