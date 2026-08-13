package org.congcong.algomentor.agent.core.execution;

/** Agent 执行任务被拒绝的稳定原因。 */
public enum AgentExecutionRejectionReason {
  GROUP_SATURATED,
  SATURATED,
  SHUTDOWN
}
