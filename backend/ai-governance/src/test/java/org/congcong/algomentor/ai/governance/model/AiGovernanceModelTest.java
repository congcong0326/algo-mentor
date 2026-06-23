package org.congcong.algomentor.ai.governance.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.auth.model.AuthRole;
import org.junit.jupiter.api.Test;

class AiGovernanceModelTest {

  @Test
  void actorDerivesAdminAndAuthenticatedFromRoles() {
    AiActor actor = new AiActor(7L, Set.of(AuthRole.USER, AuthRole.ADMIN), true);

    assertThat(actor.userId()).isEqualTo(7L);
    assertThat(actor.admin()).isTrue();
    assertThat(actor.authenticated()).isTrue();
  }

  @Test
  void anonymousActorHasNoUserIdAndIsNotAdmin() {
    AiActor actor = AiActor.anonymous();

    assertThat(actor.userId()).isNull();
    assertThat(actor.admin()).isFalse();
    assertThat(actor.authenticated()).isFalse();
  }

  @Test
  void runContextRejectsMissingRequiredFields() {
    assertThatThrownBy(() -> new AiRunContext(
        null,
        new AiActor(1L, Set.of(AuthRole.USER), true),
        AiPurpose.LEARNING_PLAN,
        AiRunSource.LEARNING_PLAN_DRAFT,
        null,
        10,
        false,
        Map.of(),
        Instant.parse("2026-06-23T00:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("run id");
  }

  @Test
  void usageAdditionKeepsNullSafeTokenCounters() {
    AiUsage usage = new AiUsage(1, 2, 3, 4, 10);

    assertThat(AiUsage.zero().plus(usage)).isEqualTo(usage);
  }
}
