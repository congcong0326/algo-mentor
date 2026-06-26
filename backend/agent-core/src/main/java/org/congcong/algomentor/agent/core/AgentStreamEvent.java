package org.congcong.algomentor.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecision;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionRequest;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;

public sealed interface AgentStreamEvent
    permits AgentStreamEvent.AgentRunStart,
    AgentStreamEvent.AgentStepStart,
    AgentStreamEvent.AgentToolStart,
    AgentStreamEvent.AgentToolEnd,
    AgentStreamEvent.ToolPermissionRequest,
    AgentStreamEvent.ToolPermissionDecision,
    AgentStreamEvent.ToolPermissionTimeout,
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

  record ToolPermissionRequest(
      String permissionRequestId,
      String runId,
      int stepIndex,
      String toolCallId,
      String toolName,
      String displayName,
      String reason,
      Map<String, Object> preview,
      Instant expiresAt
  ) implements AgentStreamEvent {

    public ToolPermissionRequest {
      validatePermissionRequestId(permissionRequestId);
      validateRunId(runId);
      validateStepIndex(stepIndex);
      validateToolCall(toolCallId, toolName);
      requireText(displayName, "Agent tool permission display name must not be blank");
      requireText(reason, "Agent tool permission reason must not be blank");
      preview = preview == null ? Map.of() : Map.copyOf(preview);
      if (preview.isEmpty()) {
        throw new IllegalArgumentException("Agent tool permission preview must not be empty");
      }
      if (expiresAt == null) {
        throw new IllegalArgumentException("Agent tool permission expiry time must not be null");
      }
    }

    public ToolPermissionRequest(AgentToolPermissionRequest request) {
      this(
          request.permissionRequestId(),
          request.runId(),
          request.stepIndex(),
          request.toolCallId(),
          request.toolName(),
          request.displayName(),
          request.reason(),
          request.preview(),
          request.expiresAt());
    }

    @Override
    public String name() {
      return AgentStreamEventNames.TOOL_PERMISSION_REQUEST;
    }
  }

  record ToolPermissionDecision(
      String permissionRequestId,
      String runId,
      int stepIndex,
      String toolCallId,
      String toolName,
      AgentToolPermissionDecisionType decision,
      String reason,
      Instant decidedAt
  ) implements AgentStreamEvent {

    public ToolPermissionDecision {
      validatePermissionRequestId(permissionRequestId);
      validateRunId(runId);
      validateStepIndex(stepIndex);
      validateToolCall(toolCallId, toolName);
      if (decision == null) {
        throw new IllegalArgumentException("Agent tool permission decision must not be null");
      }
      requireText(reason, "Agent tool permission decision reason must not be blank");
      if (decidedAt == null) {
        throw new IllegalArgumentException("Agent tool permission decision time must not be null");
      }
    }

    public ToolPermissionDecision(
        AgentToolPermissionRequest request,
        AgentToolPermissionDecision decision
    ) {
      this(
          request.permissionRequestId(),
          request.runId(),
          request.stepIndex(),
          request.toolCallId(),
          request.toolName(),
          decision.decision(),
          decision.reason(),
          decision.decidedAt());
    }

    @Override
    public String name() {
      return AgentStreamEventNames.TOOL_PERMISSION_DECISION;
    }
  }

  record ToolPermissionTimeout(
      String permissionRequestId,
      String runId,
      int stepIndex,
      String toolCallId,
      String toolName,
      String reason,
      Instant expiredAt
  ) implements AgentStreamEvent {

    public ToolPermissionTimeout {
      validatePermissionRequestId(permissionRequestId);
      validateRunId(runId);
      validateStepIndex(stepIndex);
      validateToolCall(toolCallId, toolName);
      requireText(reason, "Agent tool permission timeout reason must not be blank");
      if (expiredAt == null) {
        throw new IllegalArgumentException("Agent tool permission timeout expiry time must not be null");
      }
    }

    public ToolPermissionTimeout(
        AgentToolPermissionRequest request,
        String reason,
        Instant expiredAt
    ) {
      this(
          request.permissionRequestId(),
          request.runId(),
          request.stepIndex(),
          request.toolCallId(),
          request.toolName(),
          reason,
          expiredAt);
    }

    @Override
    public String name() {
      return AgentStreamEventNames.TOOL_PERMISSION_TIMEOUT;
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

  private static void validatePermissionRequestId(String permissionRequestId) {
    requireText(permissionRequestId, "Agent tool permission request id must not be blank");
  }

  private static void requireText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }
}
