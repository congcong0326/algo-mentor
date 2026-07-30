package org.congcong.algomentor.agent.core.runtime.api;

/** Agent 调用的治理与调度模式。 */
public enum AgentInvocationMode {
  USER_ENTRY,
  CHILD,
  BACKGROUND;

  /** 持久化到 {@code agent_run.trigger_type} 的稳定数据库值。 */
  public String databaseValue() {
    return name();
  }
}
