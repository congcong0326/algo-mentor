package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LearnerProfileAgentPropertiesTest {

  @Test
  void keepsTheDeclaredProfileToolDisabledAndBoundedByDefault() {
    LearnerProfileAgentProperties properties = new LearnerProfileAgentProperties();

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getMaxStaleRetries()).isEqualTo(1);
    assertThat(properties.getResultSummaryMaxChars()).isEqualTo(300);
    assertThatThrownBy(() -> properties.setMaxStaleRetries(2)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties.setResultSummaryMaxChars(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
