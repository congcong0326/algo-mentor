package org.congcong.algomentor.ai.governance.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;

public record AiRunStatusUpdate(
    Long admissionId,
    String runId,
    AiRunStatus status,
    String errorCode,
    String provider,
    String model,
    AiUsage usage,
    Instant completedAt
) {
}
