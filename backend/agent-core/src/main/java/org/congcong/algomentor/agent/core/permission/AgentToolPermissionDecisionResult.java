package org.congcong.algomentor.agent.core.permission;

public record AgentToolPermissionDecisionResult(
    AgentToolPermissionRequest request,
    AgentToolPermissionDecision decision,
    boolean accepted
) {

  public AgentToolPermissionDecisionResult {
    if (request == null) {
      throw new IllegalArgumentException("Agent tool permission decision result request must not be null");
    }
    if (decision == null) {
      throw new IllegalArgumentException("Agent tool permission decision result decision must not be null");
    }
  }
}
