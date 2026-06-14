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

  record AgentRunStart(String runId, String topic, int maxSteps) implements AgentStreamEvent {
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
    }

    @Override
    public String name() {
      return "agent_run_start";
    }
  }

  record AgentStepStart(String runId, int stepIndex) implements AgentStreamEvent {
    public AgentStepStart {
      validateRunId(runId);
      validateStepIndex(stepIndex);
    }

    @Override
    public String name() {
      return "agent_step_start";
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
      return "agent_tool_start";
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
      return "agent_tool_end";
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
      return "agent_step_end";
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
      return "agent_run_end";
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
      return "agent_error";
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
        return "message_start";
      }
      if (event instanceof LlmStreamEvent.ContentDelta) {
        return "content_delta";
      }
      if (event instanceof LlmStreamEvent.ToolCallStart) {
        return "tool_call_start";
      }
      if (event instanceof LlmStreamEvent.ToolCallDelta) {
        return "tool_call_delta";
      }
      if (event instanceof LlmStreamEvent.ToolCallEnd) {
        return "tool_call_end";
      }
      if (event instanceof LlmStreamEvent.MessageEnd) {
        return "message_end";
      }
      if (event instanceof LlmStreamEvent.Usage) {
        return "usage";
      }
      if (event instanceof LlmStreamEvent.Error) {
        return "error";
      }
      if (event instanceof LlmStreamEvent.Heartbeat) {
        return "heartbeat";
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
