package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LearnerMemoryRecallPropertiesTest {

  @Test
  void keepsThePracticeChatBootstrapBudgetBoundedByDefault() {
    LearnerMemoryRecallProperties properties = new LearnerMemoryRecallProperties();

    assertThat(properties.isEnabled()).isFalse();
    properties.setEnabled(true);
    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getBootstrapTokenBudget()).isEqualTo(1_000);
    properties.setBootstrapTokenBudget(1_500);
    assertThat(properties.getBootstrapTokenBudget()).isEqualTo(1_500);
    assertThatThrownBy(() -> properties.setBootstrapTokenBudget(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties.setBootstrapTokenBudget(1_501))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
