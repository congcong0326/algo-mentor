package org.congcong.algomentor.agent.core;

/**
 * Agent loop 向单个下游同步投递事件的内部边界。
 */
@FunctionalInterface
public interface AgentStreamEventSink {

  /**
   * 投递一个事件。
   *
   * @return 事件已交给下游时返回 {@code true}；流已取消或终止时返回 {@code false}
   */
  boolean emit(AgentStreamEvent event);
}
