package org.congcong.algomentor.ai.governance.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AiBusinessScenarioTest {

  @Test
  void registersUniqueCompleteScenarioDescriptorsWithoutAiDebug() {
    assertThat(Arrays.stream(AiBusinessScenario.values()).map(AiBusinessScenario::code))
        .doesNotHaveDuplicates()
        .doesNotContain("ai-debug");
    assertThat(AiBusinessScenario.values())
        .allSatisfy(scenario -> {
          assertThat(scenario.categoryCode()).isNotBlank();
          assertThat(scenario.displayNameZh()).isNotBlank();
          assertThat(scenario.displayNameEn()).isNotBlank();
          assertThat(scenario.descriptionZh()).isNotBlank();
          assertThat(scenario.descriptionEn()).isNotBlank();
        });
  }

  @Test
  void mapsLegacySourcesToBusinessScenariosExceptAiDebug() {
    assertThat(AiRunSource.PROBLEM_DETAIL.businessScenario())
        .contains(AiBusinessScenario.TOPIC_EXPLANATION);
    assertThat(AiRunSource.AI_DEBUG.businessScenario()).isEmpty();
  }

  @Test
  void resolvesRegisteredCodeOnly() {
    assertThat(AiBusinessScenario.fromCode(" PRACTICE-CHAT "))
        .isEqualTo(AiBusinessScenario.PRACTICE_CHAT);
    assertThatThrownBy(() -> AiBusinessScenario.fromCode("ai-debug"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown AI business scenario");
  }
}
