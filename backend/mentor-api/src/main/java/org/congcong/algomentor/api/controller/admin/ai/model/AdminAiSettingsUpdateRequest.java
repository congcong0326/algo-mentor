package org.congcong.algomentor.api.controller.admin.ai.model;

public record AdminAiSettingsUpdateRequest(Boolean aiEnabled, Integer defaultDailyRequestLimit) {
}
