package org.congcong.algomentor.agent.core.runtime.definition;

/** 第一阶段 Definition 的 loop 边界，仅声明最大 step 数。 */
public record AgentLoopPolicy(int maxSteps) {

  public AgentLoopPolicy {
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Agent loop policy max steps must be positive");
    }
  }
}
