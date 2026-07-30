package org.congcong.algomentor.agent.runtime.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.congcong.algomentor.agent.core.runtime.definition.AgentKey;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.junit.jupiter.api.Test;

class AgentGovernanceScenarioCatalogTest {

  @Test
  void mapsEveryRegisteredBusinessScenarioThroughItsStableAgentKey() {
    AgentGovernanceScenarioCatalog catalog = new AgentGovernanceScenarioCatalog();

    for (AiBusinessScenario scenario : AiBusinessScenario.values()) {
      AgentGovernanceScenario resolved = catalog.resolve(new AgentKey<>(scenario.code(), String.class));

      assertThat(resolved.businessScenario()).isEqualTo(scenario);
      assertThat(resolved.runSource().businessScenario()).contains(scenario);
      assertThat(resolved.purpose()).isNotNull();
    }
  }

  @Test
  void rejectsKeysThatAreNotInTheBusinessScenarioDirectory() {
    AgentGovernanceScenarioCatalog catalog = new AgentGovernanceScenarioCatalog();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> catalog.resolve(new AgentKey<>("unknown-agent", String.class)))
        .withMessage("Unknown AI business scenario: unknown-agent");
  }
}
