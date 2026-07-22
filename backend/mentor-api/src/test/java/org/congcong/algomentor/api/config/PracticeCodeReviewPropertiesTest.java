package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PracticeCodeReviewPropertiesTest {

  @Test
  void remainsDisabledUntilExplicitlyEnabled() {
    PracticeCodeReviewProperties properties = new PracticeCodeReviewProperties();

    assertThat(properties.isEnabled()).isFalse();

    properties.setEnabled(true);

    assertThat(properties.isEnabled()).isTrue();
  }
}
