package org.congcong.algomentor.ai.governance.completion;

import java.util.Map;
import org.congcong.algomentor.ai.governance.accounting.AiLlmCallKind;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;

/** 由业务服务构造的受信直接调用治理上下文。 */
public record AiCompletionContext(
    AiCompletionMode mode,
    long userId,
    String runId,
    AiPurpose purpose,
    AiRunSource source,
    AiLlmCallKind callKind,
    Integer stepIndex,
    String quotaScope,
    int requestSize,
    Map<String, Object> metadata
) {

  public AiCompletionContext {
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (purpose == null || source == null || callKind == null) {
      throw new IllegalArgumentException("purpose, source, and callKind must not be null");
    }
    if (stepIndex != null && stepIndex < 1) {
      throw new IllegalArgumentException("stepIndex must be positive when present");
    }
    if (requestSize < 0) {
      throw new IllegalArgumentException("requestSize must not be negative");
    }
    quotaScope = quotaScope == null || quotaScope.isBlank() ? "ALL" : quotaScope.trim();
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public static AiCompletionContext userEntry(
      long userId,
      String runId,
      AiPurpose purpose,
      AiRunSource source,
      int requestSize
  ) {
    return new AiCompletionContext(
        AiCompletionMode.USER_ENTRY,
        userId,
        runId,
        purpose,
        source,
        AiLlmCallKind.DIRECT,
        null,
        "ALL",
        requestSize,
        Map.of());
  }

  public static AiCompletionContext parentRun(
      long userId,
      String runId,
      AiPurpose purpose,
      AiRunSource source,
      int stepIndex
  ) {
    return new AiCompletionContext(
        AiCompletionMode.PARENT_RUN,
        userId,
        runId,
        purpose,
        source,
        AiLlmCallKind.DIRECT,
        stepIndex,
        "ALL",
        0,
        Map.of());
  }

  public static AiCompletionContext background(
      long userId,
      AiPurpose purpose,
      AiRunSource source,
      String quotaScope,
      int requestSize
  ) {
    return new AiCompletionContext(
        AiCompletionMode.BACKGROUND,
        userId,
        null,
        purpose,
        source,
        AiLlmCallKind.BACKGROUND,
        null,
        quotaScope,
        requestSize,
        Map.of());
  }
}
