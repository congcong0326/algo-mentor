package org.congcong.algomentor.agent.runtime.governance;

import java.util.Objects;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;

/** 一个 Agent key 对应的不可覆盖治理场景目录项。 */
public record AgentGovernanceScenario(
    AiBusinessScenario businessScenario,
    AiRunSource runSource,
    AiPurpose purpose
) {

  public AgentGovernanceScenario {
    businessScenario = Objects.requireNonNull(businessScenario, "AI business scenario must not be null");
    runSource = Objects.requireNonNull(runSource, "AI run source must not be null");
    purpose = Objects.requireNonNull(purpose, "AI purpose must not be null");
    if (runSource.businessScenario().filter(businessScenario::equals).isEmpty()) {
      throw new IllegalArgumentException("AI run source does not match its business scenario");
    }
  }
}
