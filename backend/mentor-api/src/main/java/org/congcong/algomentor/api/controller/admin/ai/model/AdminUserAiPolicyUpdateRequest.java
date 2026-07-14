package org.congcong.algomentor.api.controller.admin.ai.model;

public record AdminUserAiPolicyUpdateRequest(
    Boolean aiEnabledOverride,
    Integer dailyRequestLimitOverride
) {
}
