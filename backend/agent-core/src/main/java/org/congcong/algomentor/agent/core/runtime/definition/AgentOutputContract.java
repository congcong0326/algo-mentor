package org.congcong.algomentor.agent.core.runtime.definition;

import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;

/** 复用既有执行配置表达文本或结构化输出，不建立新的 schema DSL。 */
public record AgentOutputContract(AgentExecutionOptions executionOptions) {

  public AgentOutputContract {
    executionOptions = Objects.requireNonNull(executionOptions, "Agent output execution options must not be null");
  }

  public static AgentOutputContract defaults() {
    return new AgentOutputContract(AgentExecutionOptions.defaults());
  }
}
