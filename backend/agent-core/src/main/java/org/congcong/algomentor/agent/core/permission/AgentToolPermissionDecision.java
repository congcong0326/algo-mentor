package org.congcong.algomentor.agent.core.permission;

import java.time.Instant;

public record AgentToolPermissionDecision(
    String permissionRequestId,
    AgentToolPermissionDecisionType decision,
    String reason,
    Long userId,
    Instant decidedAt
) {

  public AgentToolPermissionDecision {
    if (permissionRequestId == null || permissionRequestId.isBlank()) {
      throw new IllegalArgumentException("Agent tool permission decision request id must not be blank");
    }
    if (decision == null) {
      throw new IllegalArgumentException("Agent tool permission decision type must not be null");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Agent tool permission decision reason must not be blank");
    }
    if (userId == null || userId < 1) {
      throw new IllegalArgumentException("Agent tool permission decision user id must be positive");
    }
    if (decidedAt == null) {
      throw new IllegalArgumentException("Agent tool permission decision time must not be null");
    }
  }
}
