package org.congcong.algomentor.agent.core;

/**
 * Agent 层最终结构化输出配置。
 */
public record AgentStructuredOutputOptions(
    StructuredOutputStrategy strategy,
    String schemaName,
    String schemaVersion,
    boolean required
) {

  public AgentStructuredOutputOptions {
    strategy = strategy == null ? StructuredOutputStrategy.NONE : strategy;
  }

  public static AgentStructuredOutputOptions none() {
    return new AgentStructuredOutputOptions(StructuredOutputStrategy.NONE, null, null, false);
  }
}
