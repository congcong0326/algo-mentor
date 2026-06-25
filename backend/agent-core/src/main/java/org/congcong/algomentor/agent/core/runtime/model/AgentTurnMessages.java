package org.congcong.algomentor.agent.core.runtime.model;

import java.util.Optional;

public final class AgentTurnMessages {

  private final long runId;
  private final long turnId;
  private final AgentMessage userMessage;
  private final AgentMessage assistantMessage;

  public AgentTurnMessages(long runId, long turnId, AgentMessage userMessage, AgentMessage assistantMessage) {
    if (runId < 1 || turnId < 1) {
      throw new IllegalArgumentException("Agent turn message ids must be positive");
    }
    if (userMessage == null || userMessage.role() != AgentMessage.Role.USER) {
      throw new IllegalArgumentException("Agent turn user message is required");
    }
    if (assistantMessage != null && assistantMessage.role() != AgentMessage.Role.ASSISTANT) {
      throw new IllegalArgumentException("Agent turn assistant message must have assistant role");
    }
    this.runId = runId;
    this.turnId = turnId;
    this.userMessage = userMessage;
    this.assistantMessage = assistantMessage;
  }

  public long runId() {
    return runId;
  }

  public long turnId() {
    return turnId;
  }

  public AgentMessage userMessage() {
    return userMessage;
  }

  public Optional<AgentMessage> assistantMessage() {
    return Optional.ofNullable(assistantMessage);
  }
}
