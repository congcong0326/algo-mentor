package org.congcong.algomentor.ai.governance.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;

public record AiLlmCallUsageUpdate(
    String callId,
    AiLlmCallStatus status,
    String provider,
    String model,
    String errorCode,
    AiUsage usage,
    Instant completedAt
) {
}
