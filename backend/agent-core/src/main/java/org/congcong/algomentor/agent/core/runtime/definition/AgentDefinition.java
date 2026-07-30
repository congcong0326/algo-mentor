package org.congcong.algomentor.agent.core.runtime.definition;

import java.util.List;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;

/** 不保存单次调用状态的 Agent 场景执行定义。 */
public interface AgentDefinition<I> {

  AgentKey<I> key();

  AgentLoopPolicy loopPolicy();

  /** 当前 Definition 在单次 run 中允许暴露给模型并执行的工具名称。 */
  default List<String> allowedToolNames() {
    return List.of();
  }

  AgentOutputContract outputContract();

  AgentPreparedRequest prepare(I input, AgentInvocationContext context);
}
