package org.congcong.algomentor.agent.core.execution;

/**
 * Agent loop 后台任务执行边界。
 *
 * <p>生产环境由 Spring 管理的专用线程池实现，agent-core 只依赖提交与关停状态契约。</p>
 */
public interface AgentExecutor {

  void execute(Runnable task);

  boolean isShutdown();
}
