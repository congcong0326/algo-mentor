package org.congcong.algomentor.api.agent.model;

import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionResult;

public record AgentToolPermissionDecisionResponse(
    String permissionRequestId,
    String decision,
    boolean accepted
) {

  public static AgentToolPermissionDecisionResponse fromResult(AgentToolPermissionDecisionResult result) {
    return new AgentToolPermissionDecisionResponse(
        result.decision().permissionRequestId(),
        result.decision().decision().name(),
        result.accepted());
  }
}
