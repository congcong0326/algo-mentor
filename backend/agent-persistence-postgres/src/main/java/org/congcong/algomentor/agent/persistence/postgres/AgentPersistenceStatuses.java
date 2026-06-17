package org.congcong.algomentor.agent.persistence.postgres;

/**
 * Agent PostgreSQL 持久化表中的状态值。
 */
public final class AgentPersistenceStatuses {

  /**
   * run、step 或 tool call 正在执行。
   */
  public static final String RUNNING = "running";

  /**
   * run、step 或 tool call 已成功完成。
   */
  public static final String SUCCEEDED = "succeeded";

  /**
   * run、step 或 tool call 执行失败。
   */
  public static final String FAILED = "failed";

  /**
   * 活跃消息状态。
   */
  public static final String ACTIVE = "active";

  private AgentPersistenceStatuses() {
  }
}
