package org.congcong.algomentor.agent.runtime.governance;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.execution.AiRunGovernanceLease;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiUsage;
import org.congcong.algomentor.llm.core.model.LlmInvocationTarget;

/** Runtime 持有的治理租约，向 Agent 执行器暴露受信 metadata 与终态收尾。 */
public final class AgentRuntimeGovernanceLease {

  private final AgentGovernanceScenario scenario;
  private final AiRunGovernanceLease delegate;

  AgentRuntimeGovernanceLease(AgentGovernanceScenario scenario, AiRunGovernanceLease delegate) {
    this.scenario = Objects.requireNonNull(scenario, "Agent governance scenario must not be null");
    this.delegate = Objects.requireNonNull(delegate, "AI governance lease must not be null");
  }

  public AgentGovernanceScenario scenario() {
    return scenario;
  }

  public Map<String, Object> metadata() {
    return delegate.metadata();
  }

  public LlmInvocationTarget invocationTarget() {
    return delegate.invocationTarget();
  }

  public Optional<AiRunAdmission> admission() {
    return delegate.admission();
  }

  public void complete(AiUsage usage, String provider, String model) {
    delegate.complete(usage, provider, model);
  }

  public void fail(AiGovernanceErrorCode errorCode, AiUsage usage, String provider, String model) {
    delegate.fail(errorCode, usage, provider, model);
  }

  public void cancel(AiUsage usage, String provider, String model) {
    delegate.cancel(usage, provider, model);
  }
}
