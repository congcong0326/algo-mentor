package org.congcong.algomentor.ai.governance.policy.runtime;

import java.time.Instant;

/** 单用户对全局 AI 策略的可选覆盖。 */
public record AiUserPolicy(
    long userId,
    Boolean aiEnabledOverride,
    Integer dailyRequestLimitOverride,
    Long updatedBy,
    Instant updatedAt
) {

  public AiUserPolicy {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    aiEnabledOverride = Boolean.TRUE.equals(aiEnabledOverride) ? null : aiEnabledOverride;
    if (dailyRequestLimitOverride != null
        && !AiRuntimePolicyConstraints.isValidDailyRequestLimit(dailyRequestLimitOverride)) {
      throw new IllegalArgumentException("dailyRequestLimitOverride is outside the supported range");
    }
  }

  public static AiUserPolicy inherited(long userId) {
    return new AiUserPolicy(userId, null, null, null, null);
  }

  public boolean inherited() {
    return aiEnabledOverride == null && dailyRequestLimitOverride == null;
  }
}
