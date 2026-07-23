package org.congcong.algomentor.agent.core.execution;

/** Agent 执行线程池与拒绝错误的公共契约常量。 */
public final class AgentExecutionConstants {

  public static final String DEFAULT_THREAD_NAME_PREFIX = "agent-loop-";
  public static final String REJECTION_REASON_METADATA_KEY = "agentExecutorRejectionReason";

  private AgentExecutionConstants() {
  }
}
