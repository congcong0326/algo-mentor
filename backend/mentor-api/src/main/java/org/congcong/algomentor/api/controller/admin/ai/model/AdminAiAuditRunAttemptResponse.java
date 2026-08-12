package org.congcong.algomentor.api.controller.admin.ai.model;

/** 同一 turn 关联的 run attempt 低敏定位信息。 */
public record AdminAiAuditRunAttemptResponse(long runId, int attemptNo, String status) {
}
