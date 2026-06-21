package org.congcong.algomentor.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

public sealed interface AgentStreamEvent
    permits AgentStreamEvent.AgentRunStart,
    AgentStreamEvent.AgentStepStart,
    AgentStreamEvent.AgentToolStart,
    AgentStreamEvent.AgentToolEnd,
    AgentStreamEvent.AgentStepEnd,
    AgentStreamEvent.AgentRunEnd,
    AgentStreamEvent.AgentError,
    AgentStreamEvent.Llm {

  String name();

  record AgentRunStart(String runId, String topic, int maxSteps, Map<String, Object> metadata)
      implements AgentStreamEvent {

    public AgentRunStart(String runId, String topic, int maxSteps) {
      this(runId, topic, maxSteps, Map.of());
    }

    public AgentRunStart {
      if (runId == null || runId.isBlank()) {
        throw new IllegalArgumentException("Agent run id must not be blank");
      }
      if (topic == null || topic.isBlank()) {
        throw new IllegalArgumentException("Agent topic must not be blank");
      }
      if (maxSteps < 1) {
        throw new IllegalArgumentException("Agent max steps must be positive");
      }
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public String name() {
      return AgentStreamEventNames.AGENT_RUN_START;
    }
  }

  record AgentStepStart(String runId, int stepIndex) implements AgentStreamEvent {
    public AgentStepStart {
      validateRunId(runId);
      validateStepIndex(stepIndex);
    }

    @Override
    public String name() {
      return AgentStreamEventNames.AGENT_STEP_START;
    }
  }

  record AgentToolStart(String runId, int stepIndex, String toolCallId, String toolName) implements AgentStreamEvent {
    public AgentToolStart {
      validateRunId(runId);
      validateStepIndex(stepIndex);
      validateToolCall(toolCallId, toolName);
    }

    @Override
    public String name() {
      return AgentStreamEventNames.AGENT_TOOL_START;
    }
  }

  record AgentToolEnd(
      String runId,
      int stepIndex,
      String toolCallId,
      String toolName,
      JsonNode result
  ) implements AgentStreamEvent {
    public AgentToolEnd {
      validateRunId(runId);
      validateStepIndex(stepIndex);
      validateToolCall(toolCallId, toolName);
      if (result == null) {
        throw new IllegalArgumentException("Agent tool result must not be null");
      }
    }

    @Override
    public String name() {
      return AgentStreamEventNames.AGENT_TOOL_END;
    }
  }

  record AgentStepEnd(
      String runId,
      int stepIndex,
      LlmFinishReason finishReason,
      int toolCallCount
  ) implements AgentStreamEvent {
    public AgentStepEnd {
      validateRunId(runId);
      validateStepIndex(stepIndex);
      finishReason = finishReason == null ? LlmFinishReason.UNKNOWN : finishReason;
      if (toolCallCount < 0) {
        throw new IllegalArgumentException("Agent step tool call count must not be negative");
      }
    }

    @Override
    public String name() {
      return AgentStreamEventNames.AGENT_STEP_END;
    }
  }

  record AgentRunEnd(String runId, int steps, LlmFinishReason finishReason, Map<String, Object> metadata)
      implements AgentStreamEvent {
    public AgentRunEnd {
      validateRunId(runId);
      if (steps < 1) {
        throw new IllegalArgumentException("Agent run steps must be positive");
      }
      finishReason = finishReason == null ? LlmFinishReason.UNKNOWN : finishReason;
      metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public String name() {
      return AgentStreamEventNames.AGENT_RUN_END;
    }
  }

  record AgentError(String runId, AgentException error) implements AgentStreamEvent {
    public AgentError {
      validateRunId(runId);
      if (error == null) {
        throw new IllegalArgumentException("Agent error must not be null");
      }
    }

    @Override
    public String name() {
      return AgentStreamEventNames.AGENT_ERROR;
    }
  }

  record Llm(LlmStreamEvent event) implements AgentStreamEvent {
    public Llm {
      if (event == null) {
        throw new IllegalArgumentException("Agent LLM stream event must not be null");
      }
    }

    @Override
    public String name() {
      if (event instanceof LlmStreamEvent.MessageStart) {
        return AgentStreamEventNames.MESSAGE_START;
      }
      if (event instanceof LlmStreamEvent.ContentDelta) {
        return AgentStreamEventNames.CONTENT_DELTA;
      }
      if (event instanceof LlmStreamEvent.ToolCallStart) {
        return AgentStreamEventNames.TOOL_CALL_START;
      }
      if (event instanceof LlmStreamEvent.ToolCallDelta) {
        return AgentStreamEventNames.TOOL_CALL_DELTA;
      }
      if (event instanceof LlmStreamEvent.ToolCallEnd) {
        return AgentStreamEventNames.TOOL_CALL_END;
      }
      if (event instanceof LlmStreamEvent.MessageEnd) {
        return AgentStreamEventNames.MESSAGE_END;
      }
      if (event instanceof LlmStreamEvent.Usage) {
        return AgentStreamEventNames.USAGE;
      }
      if (event instanceof LlmStreamEvent.Error) {
        return AgentStreamEventNames.ERROR;
      }
      if (event instanceof LlmStreamEvent.Heartbeat) {
        return AgentStreamEventNames.HEARTBEAT;
      }
      throw new IllegalArgumentException("Unsupported LLM stream event: " + event.getClass().getName());
    }
  }

  static AgentStreamEvent fromLlm(LlmStreamEvent event) {
    return new Llm(event);
  }

  private static void validateRunId(String runId) {
    if (runId == null || runId.isBlank()) {
      throw new IllegalArgumentException("Agent run id must not be blank");
    }
  }

  private static void validateStepIndex(int stepIndex) {
    if (stepIndex < 1) {
      throw new IllegalArgumentException("Agent step index must be positive");
    }
  }

  private static void validateToolCall(String toolCallId, String toolName) {
    if (toolCallId == null || toolCallId.isBlank()) {
      throw new IllegalArgumentException("Agent tool call id must not be blank");
    }
    if (toolName == null || toolName.isBlank()) {
      throw new IllegalArgumentException("Agent tool name must not be blank");
    }
  }
}
