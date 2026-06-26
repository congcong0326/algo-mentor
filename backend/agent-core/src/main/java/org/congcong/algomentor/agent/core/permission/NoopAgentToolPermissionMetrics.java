package org.congcong.algomentor.agent.core.permission;

import java.time.Duration;

public final class NoopAgentToolPermissionMetrics implements AgentToolPermissionMetrics {

  public static final NoopAgentToolPermissionMetrics INSTANCE = new NoopAgentToolPermissionMetrics();

  private NoopAgentToolPermissionMetrics() {
  }

  @Override
  public void recordHookDecision(
      String toolName,
      AgentToolPermissionBehavior behavior,
      String policySource
  ) {
  }

  @Override
  public void recordPermissionRequest(
      String toolName,
      String policySource
  ) {
  }

  @Override
  public void recordUserDecision(
      String toolName,
      AgentToolPermissionDecisionType decision
  ) {
  }

  @Override
  public void recordTimeout(String toolName) {
  }

  @Override
  public void recordLatency(
      String toolName,
      String outcome,
      Duration latency
  ) {
  }

  @Override
  public void recordHighPermissionExecution(
      String toolName,
      String policySource
  ) {
  }
}
