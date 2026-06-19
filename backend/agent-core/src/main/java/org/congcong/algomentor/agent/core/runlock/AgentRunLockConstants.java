package org.congcong.algomentor.agent.core.runlock;

public final class AgentRunLockConstants {

  /**
   * task 级 Agent run 锁 key 前缀。
   */
  public static final String TASK_LOCK_KEY_PREFIX = "agent-run:task:";

  /**
   * 写入 AgentRequest metadata 的持锁 token 字段名。
   */
  public static final String LOCK_TOKEN_METADATA_KEY = "agentRunLockToken";

  /**
   * 锁 metadata 中的幂等请求键字段名。
   */
  public static final String IDEMPOTENCY_KEY_METADATA_KEY = "idempotencyKey";

  /**
   * 锁 metadata 中的 run 字符串 ID 字段名。
   */
  public static final String RUN_UUID_METADATA_KEY = "runUuid";

  private AgentRunLockConstants() {
  }
}
