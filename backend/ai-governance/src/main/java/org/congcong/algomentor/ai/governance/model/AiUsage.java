package org.congcong.algomentor.ai.governance.model;

public record AiUsage(
    long inputTokens,
    long outputTokens,
    long cachedTokens,
    long reasoningTokens,
    long totalTokens
) {

  public static AiUsage zero() {
    return new AiUsage(0, 0, 0, 0, 0);
  }

  public AiUsage plus(AiUsage other) {
    AiUsage value = other == null ? zero() : other;
    return new AiUsage(
        inputTokens + value.inputTokens(),
        outputTokens + value.outputTokens(),
        cachedTokens + value.cachedTokens(),
        reasoningTokens + value.reasoningTokens(),
        totalTokens + value.totalTokens());
  }
}
