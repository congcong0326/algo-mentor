package org.congcong.algomentor.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.congcong.algomentor.identity.model.AuthRole;
import org.junit.jupiter.api.Test;

class AuthAuthoritiesTest {

  @Test
  void administratorsReceiveTheReadOnlyAiRunAuthority() {
    assertThat(AuthAuthorities.fromRoles(List.of(AuthRole.ADMIN)))
        .extracting(authority -> authority.getAuthority())
        .contains("ROLE_ADMIN", "ai-run:read");
  }

  @Test
  void ordinaryUsersDoNotReceiveTheReadOnlyAiRunAuthority() {
    assertThat(AuthAuthorities.fromRoles(List.of(AuthRole.USER)))
        .extracting(authority -> authority.getAuthority())
        .doesNotContain("ai-run:read");
  }
}
