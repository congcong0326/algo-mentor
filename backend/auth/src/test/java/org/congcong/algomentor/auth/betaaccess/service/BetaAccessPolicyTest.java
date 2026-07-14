package org.congcong.algomentor.auth.betaaccess.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.betaaccess.model.BetaAccessDecision;
import org.congcong.algomentor.auth.betaaccess.model.BetaAccessSettings;
import org.congcong.algomentor.auth.betaaccess.repository.BetaAccessRepository;
import org.congcong.algomentor.auth.service.AdminEmailRoleService;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;

class BetaAccessPolicyTest {

  private final BetaAccessRepository repository = mock(BetaAccessRepository.class);
  private final AdminEmailRoleService adminEmailRoleService = new AdminEmailRoleService(
      mock(IdentityUserRepository.class),
      List.of("emergency@example.com"));
  private final BetaAccessPolicy policy = new BetaAccessPolicy(repository, adminEmailRoleService);

  @Test
  void followsDisabledAdminInvalidAllowedAndDeniedOrder() {
    when(repository.findSettings()).thenReturn(Optional.of(settings(false)));
    assertThat(policy.evaluate(null, false)).isEqualTo(BetaAccessDecision.ALLOWED_ALLOWLIST_DISABLED);

    when(repository.findSettings()).thenReturn(Optional.of(settings(true)));
    assertThat(policy.evaluate("member@example.com", true)).isEqualTo(BetaAccessDecision.ALLOWED_TRUSTED_ADMIN);
    assertThat(policy.evaluate("emergency@example.com", false))
        .isEqualTo(BetaAccessDecision.ALLOWED_TRUSTED_ADMIN);
    assertThat(policy.evaluate("invalid address", false)).isEqualTo(BetaAccessDecision.DENIED_INVALID_EMAIL);

    when(repository.isAllowedEmail("member@example.com")).thenReturn(true);
    assertThat(policy.evaluate(" MEMBER@example.com ", false)).isEqualTo(BetaAccessDecision.ALLOWED_EMAIL);
    assertThat(policy.evaluate("other@example.com", false))
        .isEqualTo(BetaAccessDecision.DENIED_EMAIL_NOT_ALLOWED);
  }

  private static BetaAccessSettings settings(boolean enabled) {
    return new BetaAccessSettings((short) 1, enabled, null, null, Instant.EPOCH);
  }
}
