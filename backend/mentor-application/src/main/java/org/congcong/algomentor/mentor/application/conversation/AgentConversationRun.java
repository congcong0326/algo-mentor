package org.congcong.algomentor.mentor.application.conversation;

import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;

public record AgentConversationRun(
    long taskId,
    long turnId,
    long runId,
    String runUuid,
    AgentRequest agentRequest
) {

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
  }

  public boolean idempotentReplay() {
    return Boolean.TRUE.equals(agentRequest.metadata().get(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY));
  }
}
