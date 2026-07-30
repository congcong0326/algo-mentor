package org.congcong.algomentor.agent.core.runtime.api;

import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;

/** 业务层调用 Agent 的统一入口。 */
public interface AgentRuntime {

  AgentRunResult execute(AgentInvocation<?> invocation);

  Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation);
}
