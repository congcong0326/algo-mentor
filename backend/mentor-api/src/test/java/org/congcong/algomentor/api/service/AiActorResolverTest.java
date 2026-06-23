package org.congcong.algomentor.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.junit.jupiter.api.Test;

class AiActorResolverTest {

  @Test
  void resolvesActorFromCurrentUserProvider() {
    CurrentUserIdProvider provider = () -> Optional.of(new AuthenticatedUserPrincipal(
        7L, "a@example.com", "A", null, List.of(AuthRole.USER), AuthUserStatus.ACTIVE));
    var actor = new AiActorResolver(provider).currentActor();

    assertThat(actor.userId()).isEqualTo(7L);
    assertThat(actor.roles()).containsExactly(AuthRole.USER);
    assertThat(actor.authenticated()).isTrue();
  }

  @Test
  void returnsAnonymousWhenCurrentUserMissing() {
    var actor = new AiActorResolver(Optional::empty).currentActor();

    assertThat(actor.authenticated()).isFalse();
    assertThat(actor.userId()).isNull();
  }
}
