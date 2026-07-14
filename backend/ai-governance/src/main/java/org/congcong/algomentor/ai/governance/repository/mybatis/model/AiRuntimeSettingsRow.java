package org.congcong.algomentor.ai.governance.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.ai.governance.policy.runtime.AiRuntimeSettings;

public record AiRuntimeSettingsRow(
    int id,
    boolean aiEnabled,
    int defaultDailyRequestLimit,
    Long updatedBy,
    Instant updatedAt
) {

  public AiRuntimeSettings toDomain() {
    return new AiRuntimeSettings(aiEnabled, defaultDailyRequestLimit, updatedBy, updatedAt);
  }
}
