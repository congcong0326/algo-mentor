package org.congcong.algomentor.api.agent.execution;

/** Agent executor Micrometer 指标名称。 */
public final class AgentExecutorMetrics {

  public static final String ACTIVE = "algo.mentor.agent.executor.active";
  public static final String POOL_SIZE = "algo.mentor.agent.executor.pool.size";
  public static final String COMPLETED = "algo.mentor.agent.executor.completed";
  public static final String REJECTED = "algo.mentor.agent.executor.rejected";
  public static final String QUEUE_SIZE = "algo.mentor.agent.executor.queue.size";
  public static final String GROUP_LIMIT = "algo.mentor.agent.executor.group.limit";
  public static final String GROUP_ACTIVE = "algo.mentor.agent.executor.group.active";
  public static final String GROUP_AVAILABLE = "algo.mentor.agent.executor.group.available";
  public static final String GROUP_COMPLETED = "algo.mentor.agent.executor.group.completed";
  public static final String GROUP_REJECTED = "algo.mentor.agent.executor.group.rejected";

  private AgentExecutorMetrics() {
  }
}
