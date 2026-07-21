package org.congcong.algomentor.mentor.application.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LearnerProfileContentPolicyTest {

  private final LearnerProfileContentPolicy policy = new LearnerProfileContentPolicy(10);

  @Test
  void normalizesContentAtTheConfiguredBoundary() {
    assertThat(policy.validateAndNormalize("  1234567890  ")).isEqualTo("1234567890");
  }

  @Test
  void rejectsBlankOversizedAndSecretLikeContent() {
    assertThatThrownBy(() -> policy.validateAndNormalize(" ")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy.validateAndNormalize("12345678901")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LearnerProfileContentPolicy(100)
        .validateAndNormalize("api_key=12345678901234567890")).isInstanceOf(IllegalArgumentException.class);
  }
}
