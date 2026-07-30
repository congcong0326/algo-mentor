package org.congcong.algomentor.agent.core.runtime.model;

import java.util.Map;
import org.congcong.algomentor.agent.core.AgentLoopDefaults;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;

public record PreparedAgentRun(
    long taskId,
    long turnId,
    long runId,
    String runUuid,
    String requestId,
    String systemPrompt,
    String activeSummary,
    Map<String, Object> metadata,
    String agentKey,
    AgentInvocationMode mode,
    Long parentRunId,
    Integer parentStepIndex,
    Long retryOfRunId,
    int maxSteps
) {

  public PreparedAgentRun(
      long taskId,
      long turnId,
      long runId,
      String runUuid,
      String requestId,
      String systemPrompt,
      String activeSummary,
      Map<String, Object> metadata
  ) {
    this(
        taskId,
        turnId,
        runId,
        runUuid,
        requestId,
        systemPrompt,
        activeSummary,
        metadata,
        null,
        AgentInvocationMode.USER_ENTRY,
        null,
        null,
        null,
        AgentLoopDefaults.DEFAULT_MAX_STEPS);
  }

  public PreparedAgentRun {
    if (taskId < 1 || turnId < 1 || runId < 1) {
      throw new IllegalArgumentException("Conversation draft ids must be positive");
    }
    if (runUuid == null || runUuid.isBlank()) {
      throw new IllegalArgumentException("Conversation draft run uuid must not be blank");
    }
    if (requestId != null && requestId.isBlank()) {
      throw new IllegalArgumentException("Conversation draft request id must not be blank");
    }
    agentKey = agentKey == null ? null : agentKey.trim();
    if (agentKey != null && agentKey.isEmpty()) {
      throw new IllegalArgumentException("Conversation draft agent key must not be blank");
    }
    mode = mode == null ? AgentInvocationMode.USER_ENTRY : mode;
    if ((parentRunId == null) != (parentStepIndex == null)) {
      throw new IllegalArgumentException("Conversation draft parent run and step must be provided together");
    }
    if (parentRunId != null && parentRunId < 1) {
      throw new IllegalArgumentException("Conversation draft parent run id must be positive");
    }
    if (parentStepIndex != null && parentStepIndex < 1) {
      throw new IllegalArgumentException("Conversation draft parent step index must be positive");
    }
    if (retryOfRunId != null && retryOfRunId < 1) {
      throw new IllegalArgumentException("Conversation draft retry source run id must be positive");
    }
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Conversation draft max steps must be positive");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
