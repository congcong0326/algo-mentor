package org.congcong.algomentor.ai.governance.admission;

import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicy;

public record AiRunAdmission(
    Long admissionId,
    String runId,
    long userId,
    AiPurpose purpose,
    AiRunSource source,
    AiRunStatus status,
    String quotaScope,
    AgentRunLockToken lockToken,
    AiPurposePolicy policy,
    Map<String, Object> metadata,
    Instant admittedAt
) {

  public AiRunAdmission {
    if (runId == null || runId.isBlank()) {
      throw new IllegalArgumentException("AI run admission run id must not be blank");
    }
    if (userId < 1) {
      throw new IllegalArgumentException("AI run admission user id must be positive");
    }
    if (purpose == null) {
      throw new IllegalArgumentException("AI run admission purpose must not be null");
    }
    if (source == null) {
      throw new IllegalArgumentException("AI run admission source must not be null");
    }
    if (status == null) {
      throw new IllegalArgumentException("AI run admission status must not be null");
    }
    if (quotaScope == null || quotaScope.isBlank()) {
      throw new IllegalArgumentException("AI run admission quota scope must not be blank");
    }
    if (policy == null) {
      throw new IllegalArgumentException("AI run admission policy must not be null");
    }
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    admittedAt = admittedAt == null ? Instant.now() : admittedAt;
  }
}
