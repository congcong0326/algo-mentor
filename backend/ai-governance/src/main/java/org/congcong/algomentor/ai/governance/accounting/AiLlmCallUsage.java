package org.congcong.algomentor.ai.governance.accounting;

import java.time.Instant;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;

/** 调用级台账的一条最终视图，供持久化与测试使用。 */
public record AiLlmCallUsage(
    AiLlmCallContext context,
    AiLlmCallStatus status,
    String provider,
    String model,
    LlmReasoningEffort reasoningEffort,
    String errorCode,
    AiUsage usage,
    Instant startedAt,
    Instant completedAt
) {

  public AiLlmCallUsage {
    usage = usage == null ? AiUsage.zero() : usage;
  }
}
