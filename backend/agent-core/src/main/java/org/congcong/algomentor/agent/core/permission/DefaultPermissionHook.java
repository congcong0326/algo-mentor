package org.congcong.algomentor.agent.core.permission;

public final class DefaultPermissionHook implements AgentToolPermissionHook {

  public static final int DEFAULT_ORDER = Integer.MAX_VALUE;
  public static final String POLICY_SOURCE = "default-permission-hook";

  @Override
  public int order() {
    return DEFAULT_ORDER;
  }

  @Override
  public AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check) {
    if (check == null) {
      throw new IllegalArgumentException("Agent tool permission check must not be null");
    }
    return AgentToolPermissionDecisionPlan.allow(POLICY_SOURCE);
  }
}
