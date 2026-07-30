package org.congcong.algomentor.ai.governance.execution;

import java.util.Map;
import java.util.Objects;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallKind;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;

/** 由受信执行器提交给治理租约的固定身份与调用属性。 */
public record AiRunGovernanceRequest(
    AiRunGovernanceMode mode,
    String runId,
    long userId,
    AiPurpose purpose,
    AiRunSource source,
    String idempotencyKey,
    int requestSize,
    boolean streaming,
    AiLlmCallKind callKind,
    String quotaScope,
    Map<String, Object> metadata
) {

  public AiRunGovernanceRequest {
    mode = Objects.requireNonNull(mode, "AI governance mode must not be null");
    if (mode != AiRunGovernanceMode.BACKGROUND && (runId == null || runId.isBlank())) {
      throw new IllegalArgumentException("AI governance run id is required outside background mode");
    }
    runId = runId == null ? null : runId.trim();
    if (userId < 1) {
      throw new IllegalArgumentException("AI governance user id must be positive");
    }
    purpose = Objects.requireNonNull(purpose, "AI governance purpose must not be null");
    source = Objects.requireNonNull(source, "AI governance source must not be null");
    if (idempotencyKey != null && idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("AI governance idempotency key must not be blank");
    }
    idempotencyKey = idempotencyKey == null ? null : idempotencyKey.trim();
    if (requestSize < 0) {
      throw new IllegalArgumentException("AI governance request size must not be negative");
    }
    callKind = Objects.requireNonNull(callKind, "AI governance call kind must not be null");
    quotaScope = quotaScope == null || quotaScope.isBlank() ? "ALL" : quotaScope.trim();
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
