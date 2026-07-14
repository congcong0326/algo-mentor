package org.congcong.algomentor.api.controller.admin.ai.model;

import java.time.Instant;

public record AdminUserAiPolicyResponse(
    long userId,
    boolean globalAiEnabled,
    Boolean aiEnabledOverride,
    boolean effectiveAiEnabled,
    String effectiveDisabledReason,
    int globalDefaultDailyRequestLimit,
    Integer dailyRequestLimitOverride,
    int effectiveDailyRequestLimit,
    Long updatedBy,
    String updatedByDisplayName,
    Instant updatedAt
) {
}
