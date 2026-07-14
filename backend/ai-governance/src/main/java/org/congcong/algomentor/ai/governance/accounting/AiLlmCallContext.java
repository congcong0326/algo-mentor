package org.congcong.algomentor.ai.governance.accounting;

import java.util.UUID;

/** 从受信 request metadata 解析出的调用级台账关联信息。 */
public record AiLlmCallContext(
    String callId,
    String runId,
    Long userId,
    String purpose,
    String source,
    AiLlmCallKind callKind,
    Integer stepIndex,
    String quotaScope,
    boolean missingTrustedContext
) {

  public static final String UNKNOWN = "UNKNOWN";

  public AiLlmCallContext {
    if (callId == null || callId.isBlank()) {
      throw new IllegalArgumentException("callId must not be blank");
    }
    purpose = normalizedOrUnknown(purpose);
    source = normalizedOrUnknown(source);
    callKind = callKind == null ? AiLlmCallKind.DIRECT : callKind;
    if (stepIndex != null && stepIndex < 1) {
      throw new IllegalArgumentException("stepIndex must be positive when present");
    }
    quotaScope = quotaScope == null || quotaScope.isBlank() ? "ALL" : quotaScope.trim();
  }

  public static AiLlmCallContext unknown(Integer stepIndex) {
    return new AiLlmCallContext(
        UUID.randomUUID().toString(),
        null,
        null,
        UNKNOWN,
        UNKNOWN,
        stepIndex == null ? AiLlmCallKind.DIRECT : AiLlmCallKind.AGENT_STEP,
        stepIndex,
        "ALL",
        true);
  }

  private static String normalizedOrUnknown(String value) {
    return value == null || value.isBlank() ? UNKNOWN : value.trim();
  }
}
