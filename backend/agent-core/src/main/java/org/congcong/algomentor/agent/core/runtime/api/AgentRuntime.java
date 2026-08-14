package org.congcong.algomentor.agent.core.runtime.api;

import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;

/** 业务层调用 Agent 的统一入口。 */
public interface AgentRuntime {

  AgentRunResult execute(AgentInvocation<?> invocation);

  Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation);

  /**
   * 准备一次可由外部事件出口启动的流式运行。
   *
   * <p>默认实现保留旧 Runtime 测试替身的二进制/源码兼容；生产 Runtime 必须覆盖该方法。</p>
   */
  default AgentPreparedStream prepareStream(AgentInvocation<?> invocation) {
    throw new UnsupportedOperationException("Prepared Agent streams are not supported by this runtime");
  }
}
