package org.congcong.algomentor.agent.core;

import java.util.Optional;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;

/** 从 Agent run 的进程内执行上下文取得已固定的 LLM 调用目标。 */
@FunctionalInterface
public interface AgentInvocationTargetResolver {

  Optional<LlmInvocationTarget> resolve(AgentRequest request);

  static AgentInvocationTargetResolver none() {
    return request -> Optional.empty();
  }
}
