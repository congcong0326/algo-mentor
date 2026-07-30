package org.congcong.algomentor.agent.core.runtime.api;

import java.util.Objects;
import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;

/** 将类型化 Agent key、业务输入和受信调用上下文绑定为一次调用。 */
public record AgentInvocation<I>(
    AgentKey<I> agentKey,
    I input,
    AgentInvocationContext context
) {

  public AgentInvocation {
    agentKey = Objects.requireNonNull(agentKey, "Agent invocation key must not be null");
    input = Objects.requireNonNull(input, "Agent invocation input must not be null");
    context = Objects.requireNonNull(context, "Agent invocation context must not be null");
    if (!agentKey.inputType().isInstance(input)) {
      throw new IllegalArgumentException(
          "Agent invocation input type does not match key: " + agentKey.value());
    }
  }
}
