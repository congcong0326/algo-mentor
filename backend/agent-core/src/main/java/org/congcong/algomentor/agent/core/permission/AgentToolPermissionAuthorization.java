package org.congcong.algomentor.agent.core.permission;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface AgentToolPermissionAuthorization
    permits AgentToolPermissionAuthorization.Allowed, AgentToolPermissionAuthorization.SyntheticResult {

  record Allowed(AgentToolPermissionDecisionPlan plan) implements AgentToolPermissionAuthorization {

    public Allowed {
      if (plan == null) {
        throw new IllegalArgumentException("Agent tool permission allow plan must not be null");
      }
      if (plan.behavior() != AgentToolPermissionBehavior.ALLOW
          && plan.behavior() != AgentToolPermissionBehavior.ASK) {
        throw new IllegalArgumentException("Agent tool permission allow authorization requires an ALLOW or ASK plan");
      }
    }
  }

  record SyntheticResult(
      JsonNode result,
      AgentToolPermissionDecisionPlan plan
  ) implements AgentToolPermissionAuthorization {

    public SyntheticResult {
      if (result == null) {
        throw new IllegalArgumentException("Agent tool permission synthetic result must not be null");
      }
      if (plan == null) {
        throw new IllegalArgumentException("Agent tool permission synthetic result plan must not be null");
      }
      if (plan.behavior() == AgentToolPermissionBehavior.ALLOW
          || plan.behavior() == AgentToolPermissionBehavior.PASSTHROUGH) {
        throw new IllegalArgumentException("Agent tool permission synthetic result requires a DENY or ASK plan");
      }
    }
  }
}
