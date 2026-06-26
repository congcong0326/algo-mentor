package org.congcong.algomentor.agent.core.permission;

public interface AgentToolPermissionHook {

  int order();

  AgentToolPermissionDecisionPlan evaluate(AgentToolPermissionCheck check);
}
