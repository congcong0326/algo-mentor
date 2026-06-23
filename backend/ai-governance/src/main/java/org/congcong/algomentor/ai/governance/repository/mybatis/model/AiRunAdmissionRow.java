package org.congcong.algomentor.ai.governance.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;

public record AiRunAdmissionRow(
    Long id,
    String runId,
    Long userId,
    AiPurpose purpose,
    AiRunSource source,
    AiRunStatus status,
    String idempotencyKey,
    int requestSize,
    String rejectionCode,
    String errorCode,
    String provider,
    String model,
    long inputTokens,
    long outputTokens,
    long cachedTokens,
    long reasoningTokens,
    long totalTokens,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
