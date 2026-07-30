package org.congcong.algomentor.agent.core.runtime.model;

import java.util.Map;
import org.congcong.algomentor.agent.core.AgentLoopDefaults;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;

public record AgentRunPreparationRequest(
    Long taskId,
    Long userId,
    String userMessage,
    String idempotencyKey,
    String systemPrompt,
    Map<String, Object> metadata,
    Map<String, Object> userMessageMetadata,
    String agentKey,
    AgentInvocationMode mode,
    Long parentRunId,
    Integer parentStepIndex,
    Long retryOfRunId,
    int maxSteps
) {

  public AgentRunPreparationRequest(
      Long taskId,
      Long userId,
      String userMessage,
      String idempotencyKey,
      String systemPrompt,
      Map<String, Object> metadata
  ) {
    this(
        taskId,
        userId,
        userMessage,
        idempotencyKey,
        systemPrompt,
        metadata,
        Map.of(),
        null,
        AgentInvocationMode.USER_ENTRY,
        null,
        null,
        null,
        AgentLoopDefaults.DEFAULT_MAX_STEPS);
  }

  public AgentRunPreparationRequest(
      Long taskId,
      Long userId,
      String userMessage,
      String idempotencyKey,
      String systemPrompt,
      Map<String, Object> metadata,
      Map<String, Object> userMessageMetadata
  ) {
    this(
        taskId,
        userId,
        userMessage,
        idempotencyKey,
        systemPrompt,
        metadata,
        userMessageMetadata,
        null,
        AgentInvocationMode.USER_ENTRY,
        null,
        null,
        null,
        AgentLoopDefaults.DEFAULT_MAX_STEPS);
  }

  public AgentRunPreparationRequest {
    if (retryOfRunId == null && (userMessage == null || userMessage.isBlank())) {
      throw new IllegalArgumentException("Agent run user message must not be blank");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Agent run idempotency key must not be blank");
    }
    if (taskId != null && taskId < 1) {
      throw new IllegalArgumentException("Agent task id must be positive");
    }
    if (userId != null && userId < 1) {
      throw new IllegalArgumentException("Agent user id must be positive");
    }
    if (taskId == null && userId == null && retryOfRunId == null) {
      throw new IllegalArgumentException("Agent run without a task requires a user id");
    }
    agentKey = agentKey == null ? null : agentKey.trim();
    if (agentKey != null && agentKey.isEmpty()) {
      throw new IllegalArgumentException("Agent key must not be blank");
    }
    mode = mode == null ? AgentInvocationMode.USER_ENTRY : mode;
    if ((parentRunId == null) != (parentStepIndex == null)) {
      throw new IllegalArgumentException("Agent parent run and step index must be provided together");
    }
    if (parentRunId != null && parentRunId < 1) {
      throw new IllegalArgumentException("Agent parent run id must be positive");
    }
    if (parentStepIndex != null && parentStepIndex < 1) {
      throw new IllegalArgumentException("Agent parent step index must be positive");
    }
    if (retryOfRunId != null && retryOfRunId < 1) {
      throw new IllegalArgumentException("Agent retry source run id must be positive");
    }
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Agent run max steps must be positive");
    }
    userMessage = userMessage == null ? null : userMessage.trim();
    idempotencyKey = idempotencyKey.trim();
    systemPrompt = systemPrompt == null ? "" : systemPrompt;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    userMessageMetadata = userMessageMetadata == null ? Map.of() : Map.copyOf(userMessageMetadata);
  }
}
