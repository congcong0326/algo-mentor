package org.congcong.algomentor.ai.governance.policy.runtime;

import java.time.Instant;

/** 数据库中唯一的全局 AI 运行策略。 */
public record AiRuntimeSettings(
    boolean aiEnabled,
    int defaultDailyRequestLimit,
    Long updatedBy,
    Instant updatedAt
) {

  public AiRuntimeSettings {
    if (!AiRuntimePolicyConstraints.isValidDailyRequestLimit(defaultDailyRequestLimit)) {
      throw new IllegalArgumentException("defaultDailyRequestLimit is outside the supported range");
    }
  }
}
