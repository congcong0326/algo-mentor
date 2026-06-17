package org.congcong.algomentor.mentor.application.conversation;

public record ContextAssemblyPolicy(
    int recentTurns,
    int tokenBudget,
    String policyName,
    String policyVersion
) {

  public static ContextAssemblyPolicy defaultPolicy() {
    return new ContextAssemblyPolicy(8, 8_000, "sliding-window-with-active-summary", "v1");
  }

  public ContextAssemblyPolicy {
    if (recentTurns < 1) {
      throw new IllegalArgumentException("Context recent turns must be positive");
    }
    if (tokenBudget < 1) {
      throw new IllegalArgumentException("Context token budget must be positive");
    }
    if (policyName == null || policyName.isBlank()) {
      throw new IllegalArgumentException("Context policy name must not be blank");
    }
    if (policyVersion == null || policyVersion.isBlank()) {
      throw new IllegalArgumentException("Context policy version must not be blank");
    }
  }
}
