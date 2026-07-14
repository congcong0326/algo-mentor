package org.congcong.algomentor.ai.governance.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.ai.governance.policy.runtime.AiUserPolicy;

public record AiUserPolicyRow(
    long userId,
    Boolean aiEnabledOverride,
    Integer dailyRequestLimitOverride,
    Long updatedBy,
    Instant updatedAt
) {

  public AiUserPolicy toDomain() {
    return new AiUserPolicy(
        userId,
        aiEnabledOverride,
        dailyRequestLimitOverride,
        updatedBy,
        updatedAt);
  }
}
