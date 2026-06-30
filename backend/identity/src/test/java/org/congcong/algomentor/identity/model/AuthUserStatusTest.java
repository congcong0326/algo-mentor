package org.congcong.algomentor.identity.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthUserStatusTest {

  @Test
  void statusIncludesDeletedAndDefaultsToActive() {
    AuthUser user = new AuthUser(
        1L,
        "user@example.com",
        "user@example.com",
        "User Name",
        null,
        null,
        Instant.parse("2026-06-30T00:00:00Z"),
        Instant.parse("2026-06-30T00:00:00Z"),
        null,
        null,
        null);

    assertThat(AuthUserStatus.valueOf("DELETED")).isEqualTo(AuthUserStatus.DELETED);
    assertThat(user.status()).isEqualTo(AuthUserStatus.ACTIVE);
  }
}
