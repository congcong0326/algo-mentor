package org.congcong.algomentor.agent.core.execution;

import java.util.Optional;

/**
 * Agent loop 后台任务执行边界。
 *
 * <p>生产环境由 Spring 管理的专用线程池实现，agent-core 只依赖提交与关停状态契约。</p>
 */
public interface AgentExecutor {

  /** 使用 Definition 声明的受信执行组提交顶层 Agent 任务。 */
  void execute(AgentExecutionGroup group, Runnable task);

  boolean isShutdown();

  /**
   * 当前线程是否正在执行由该 Agent executor 提交的任务。
   *
   * <p>默认值兼容测试替身和未声明线程归属的实现。</p>
   */
  default boolean inExecutorThread() {
    return false;
  }

  /** 当前 Agent 工作线程继承的执行组；非 Agent 工作线程返回空。 */
  default Optional<AgentExecutionGroup> currentExecutionGroup() {
    return Optional.empty();
  }
}
