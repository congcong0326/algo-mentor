package org.congcong.algomentor.ai.governance.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallKind;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallStatus;
import org.congcong.algomentor.ai.governance.model.AiUsage;

public record AiLlmCallUsageRow(
    String callId,
    String runId,
    Long userId,
    String purpose,
    String source,
    AiLlmCallKind callKind,
    Integer stepIndex,
    String provider,
    String model,
    String reasoningEffort,
    Long providerInstanceId,
    Long aiModelId,
    AiLlmCallStatus status,
    String errorCode,
    AiUsage usage,
    Instant startedAt,
    Instant completedAt
) {
}
