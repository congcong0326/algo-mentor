package org.congcong.algomentor.agent.core.compaction;

public record ToolResultCompactionPolicy(
    int inlineMaxChars,
    int previewMaxChars,
    int rangeReadMaxChars,
    boolean blobEnabled,
    int toolResultsTotalMaxChars,
    int keepRecentToolResults,
    boolean compactOldToolResults,
    int inputTokenBudget,
    int maxMessageGroups,
    int snipKeepHeadGroups,
    int snipKeepTailGroups,
    boolean groupAwareSnipEnabled,
    boolean llmCompactEnabled
) {

  public static final String POLICY_VERSION = "tool-result-compaction-v1";

  public ToolResultCompactionPolicy {
    if (inlineMaxChars < 1) {
      throw new IllegalArgumentException("inlineMaxChars must be positive");
    }
    if (previewMaxChars < 1) {
      throw new IllegalArgumentException("previewMaxChars must be positive");
    }
    if (rangeReadMaxChars < 1) {
      throw new IllegalArgumentException("rangeReadMaxChars must be positive");
    }
    if (toolResultsTotalMaxChars < 1) {
      throw new IllegalArgumentException("toolResultsTotalMaxChars must be positive");
    }
    if (keepRecentToolResults < 0) {
      throw new IllegalArgumentException("keepRecentToolResults must not be negative");
    }
    if (inputTokenBudget < 1) {
      throw new IllegalArgumentException("inputTokenBudget must be positive");
    }
    if (maxMessageGroups < 1) {
      throw new IllegalArgumentException("maxMessageGroups must be positive");
    }
    if (snipKeepHeadGroups < 0) {
      throw new IllegalArgumentException("snipKeepHeadGroups must not be negative");
    }
    if (snipKeepTailGroups < 1) {
      throw new IllegalArgumentException("snipKeepTailGroups must be positive");
    }
  }

  public static ToolResultCompactionPolicy defaults() {
    return new ToolResultCompactionPolicy(
        12_000,
        2_000,
        8_000,
        true,
        60_000,
        3,
        true,
        120_000,
        80,
        2,
        24,
        true,
        false);
  }

  public int estimatedInputCharBudget() {
    return inputTokenBudget * 4;
  }
}
