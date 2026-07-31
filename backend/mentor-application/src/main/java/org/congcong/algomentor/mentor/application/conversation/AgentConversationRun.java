package org.congcong.algomentor.mentor.application.conversation;

import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.runtime.definition.AgentRunResource;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;

public record AgentConversationRun(
    long taskId,
    long turnId,
    long runId,
    String runUuid,
    AgentRequest agentRequest,
    PreparedAgentRun preparedRun,
    AgentRunResource runResource
) {

  public AgentConversationRun(
      long taskId,
      long turnId,
      long runId,
      String runUuid,
      AgentRequest agentRequest
  ) {
    this(
        taskId,
        turnId,
        runId,
        runUuid,
        agentRequest,
        new PreparedAgentRun(
            taskId,
            turnId,
            runId,
            runUuid,
            agentRequest.requestId(),
            "",
            null,
            agentRequest.metadata()),
        AgentRunResource.none());
  }

  public AgentConversationRun {
    if (taskId < 1 || turnId < 1 || runId < 1) {
      throw new IllegalArgumentException("Conversation run ids must be positive");
    }
    if (runUuid == null || runUuid.isBlank()) {
      throw new IllegalArgumentException("Conversation run uuid must not be blank");
    }
    if (agentRequest == null) {
      throw new IllegalArgumentException("Conversation agent request must not be null");
    }
    if (preparedRun == null) {
      throw new IllegalArgumentException("Conversation prepared run must not be null");
    }
    runResource = runResource == null ? AgentRunResource.none() : runResource;
  }

  public boolean idempotentReplay() {
    return Boolean.TRUE.equals(agentRequest.metadata().get(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY));
  }
}
