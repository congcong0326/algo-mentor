package org.congcong.algomentor.ai.governance.policy;

public record AiPurposePolicy(
    boolean enabled,
    int dailyRequestLimit,
    int maxConcurrentRunsPerUser,
    int maxRequestBytes,
    int maxOutputTokens,
    int maxSteps,
    boolean streamingAllowed,
    boolean toolsAllowed,
    boolean structuredOutputRequired,
    boolean adminOnly,
    String defaultProvider,
    String defaultModel,
    String systemPolicyVersion
) {

  public AiPurposePolicy {
    if (dailyRequestLimit < 1) {
      throw new IllegalArgumentException("Daily AI request limit must be positive");
    }
    if (maxConcurrentRunsPerUser < 1) {
      throw new IllegalArgumentException("Max concurrent AI runs per user must be positive");
    }
    if (maxRequestBytes < 1) {
      throw new IllegalArgumentException("Max AI request bytes must be positive");
    }
    if (maxOutputTokens < 1) {
      throw new IllegalArgumentException("Max output tokens must be positive");
    }
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Max AI steps must be positive");
    }
  }
}
