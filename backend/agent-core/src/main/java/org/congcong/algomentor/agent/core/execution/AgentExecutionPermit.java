package org.congcong.algomentor.agent.core.execution;

/** 一次顶层 Agent 执行组准入的幂等租约。 */
public interface AgentExecutionPermit extends AutoCloseable {

  AgentExecutionGroup group();

  @Override
  void close();
}
