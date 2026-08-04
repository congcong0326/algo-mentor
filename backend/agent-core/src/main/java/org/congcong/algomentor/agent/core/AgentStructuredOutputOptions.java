package org.congcong.algomentor.agent.core;

/**
 * Agent 层最终结构化输出配置。
 */
public record AgentStructuredOutputOptions(
    StructuredOutputStrategy strategy,
    String schemaName,
    String schemaVersion,
    boolean required,
    int maxRepairAttempts
) {

  public static final int DEFAULT_REQUIRED_REPAIR_ATTEMPTS = 1;
  public static final int MAX_REPAIR_ATTEMPTS = 2;

  public AgentStructuredOutputOptions(
      StructuredOutputStrategy strategy,
      String schemaName,
      String schemaVersion,
      boolean required
  ) {
    this(
        strategy,
        schemaName,
        schemaVersion,
        required,
        required ? DEFAULT_REQUIRED_REPAIR_ATTEMPTS : 0);
  }

  public AgentStructuredOutputOptions {
    strategy = strategy == null ? StructuredOutputStrategy.NONE : strategy;
    if (maxRepairAttempts < 0 || maxRepairAttempts > MAX_REPAIR_ATTEMPTS) {
      throw new IllegalArgumentException(
          "Agent structured output repair attempts must be between 0 and " + MAX_REPAIR_ATTEMPTS);
    }
    if (!required && maxRepairAttempts > 0) {
      throw new IllegalArgumentException("Optional structured output must not enable automatic repair");
    }
  }

  public static AgentStructuredOutputOptions none() {
    return new AgentStructuredOutputOptions(StructuredOutputStrategy.NONE, null, null, false, 0);
  }
}
