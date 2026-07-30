package org.congcong.algomentor.ai.governance.execution;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;

/**
 * 一次已开始的 AI 治理执行租约。
 *
 * <p>终态方法幂等；首个终态调用负责更新准入生命周期并释放调用目标或用户锁。</p>
 */
public final class AiRunGovernanceLease {

  interface Settlement {
    void complete(AiUsage usage, String provider, String model);

    void fail(AiGovernanceErrorCode errorCode, AiUsage usage, String provider, String model);

    void cancel(AiUsage usage, String provider, String model);
  }

  private final AiRunGovernanceRequest request;
  private final AiRunAdmission admission;
  private final LlmInvocationTarget invocationTarget;
  private final Map<String, Object> metadata;
  private final Settlement settlement;
  private boolean settled;

  AiRunGovernanceLease(
      AiRunGovernanceRequest request,
      AiRunAdmission admission,
      LlmInvocationTarget invocationTarget,
      Map<String, Object> metadata,
      Settlement settlement
  ) {
    this.request = Objects.requireNonNull(request, "AI governance request must not be null");
    this.admission = admission;
    this.invocationTarget = Objects.requireNonNull(invocationTarget, "AI invocation target must not be null");
    this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    this.settlement = Objects.requireNonNull(settlement, "AI governance settlement must not be null");
  }

  public AiRunGovernanceRequest request() {
    return request;
  }

  public Optional<AiRunAdmission> admission() {
    return Optional.ofNullable(admission);
  }

  public LlmInvocationTarget invocationTarget() {
    return invocationTarget;
  }

  public Map<String, Object> metadata() {
    return metadata;
  }

  public synchronized void complete(AiUsage usage, String provider, String model) {
    if (settled) {
      return;
    }
    settled = true;
    settlement.complete(usage == null ? AiUsage.zero() : usage, provider, model);
  }

  public synchronized void fail(
      AiGovernanceErrorCode errorCode,
      AiUsage usage,
      String provider,
      String model
  ) {
    if (settled) {
      return;
    }
    settled = true;
    settlement.fail(
        errorCode == null ? AiGovernanceErrorCode.AI_UNKNOWN : errorCode,
        usage == null ? AiUsage.zero() : usage,
        provider,
        model);
  }

  public synchronized void cancel(AiUsage usage, String provider, String model) {
    if (settled) {
      return;
    }
    settled = true;
    settlement.cancel(usage == null ? AiUsage.zero() : usage, provider, model);
  }
}
