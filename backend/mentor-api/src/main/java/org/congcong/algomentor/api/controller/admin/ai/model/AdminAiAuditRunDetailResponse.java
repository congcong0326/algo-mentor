package org.congcong.algomentor.api.controller.admin.ai.model;

import java.time.Instant;
import java.util.List;

/** run 审计详情，JSON 快照继续通过 step 详情接口延迟加载。 */
public record AdminAiAuditRunDetailResponse(
    AdminAiAuditRunResponse run,
    int attemptNo,
    Long retryOfRunId,
    int maxSteps,
    String errorCode,
    String errorMessage,
    Instant diagnosticRetentionExpiresAt,
    Instant diagnosticRedactedAt,
    AdminAiAuditTurnResponse currentTurn,
    List<AdminAiAuditTurnResponse> taskTurns,
    List<AdminAiAuditStepResponse> steps,
    AdminAiAuditUsageResponse totalUsage
) {
}
