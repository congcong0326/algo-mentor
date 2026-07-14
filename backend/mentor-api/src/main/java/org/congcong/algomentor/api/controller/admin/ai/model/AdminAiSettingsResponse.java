package org.congcong.algomentor.api.controller.admin.ai.model;

import java.time.Instant;

public record AdminAiSettingsResponse(
    boolean aiEnabled,
    int defaultDailyRequestLimit,
    Long updatedBy,
    String updatedByDisplayName,
    Instant updatedAt
) {
}
