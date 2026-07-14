package org.congcong.algomentor.auth.betaaccess.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmail;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmailAddStatus;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmailRemovalResult;
import org.congcong.algomentor.auth.betaaccess.repository.BetaAccessRepository;
import org.congcong.algomentor.auth.session.AuthSessionRevocationService;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditOutcome;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.junit.jupiter.api.Test;

class BetaAccessAdminServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

  @Test
  void batchAddPreservesOrderAndDoesNotAuditDuplicates() {
    BetaAccessRepository repository = mock(BetaAccessRepository.class);
    when(repository.findAllowedEmailByNormalized("member@example.com")).thenReturn(Optional.empty());
    when(repository.insertAllowedEmail("Member@example.com", "member@example.com", 1L, NOW))
        .thenReturn(Optional.of(7L));
    List<AdminOperationAuditEvent> audits = new ArrayList<>();
    BetaAccessAdminService service = service(
        repository,
        mock(IdentityUserRepository.class),
        userId -> 0,
        audits,
        mock(BetaAllowedEmailRemovalExecutor.class),
        () -> { });

    var result = service.addEmails(List.of(
        "Member@example.com",
        " member@example.com ",
        "invalid address"), 1L);

    assertThat(result.addedCount()).isEqualTo(1);
    assertThat(result.existingCount()).isEqualTo(1);
    assertThat(result.invalidCount()).isEqualTo(1);
    assertThat(result.results()).extracting(item -> item.status()).containsExactly(
        BetaAllowedEmailAddStatus.ADDED,
        BetaAllowedEmailAddStatus.EXISTING,
        BetaAllowedEmailAddStatus.INVALID);
    assertThat(audits).hasSize(2);
    assertThat(audits).filteredOn(event -> event.outcome() == AdminAuditOutcome.SUCCESS)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.action()).isEqualTo(AdminAuditAction.BETA_ALLOWED_EMAIL_ADD);
          assertThat(event.targetRef()).isEqualTo("7");
        });
    assertThat(audits).filteredOn(event -> event.outcome() == AdminAuditOutcome.FAILURE)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.action()).isEqualTo(AdminAuditAction.BETA_ALLOWED_EMAIL_ADD);
          assertThat(event.targetRef()).isNull();
        });
  }

  @Test
  void removalStaysDeletedWhenSessionRevocationFails() {
    BetaAccessRepository repository = mock(BetaAccessRepository.class);
    IdentityUserRepository identityRepository = mock(IdentityUserRepository.class);
    BetaAllowedEmailRemovalExecutor removalExecutor = mock(BetaAllowedEmailRemovalExecutor.class);
    BetaAllowedEmail allowedEmail = new BetaAllowedEmail(
        7L,
        "member@example.com",
        "member@example.com",
        1L,
        "Admin",
        NOW,
        42L,
        AuthUserStatus.ACTIVE);
    when(removalExecutor.remove(7L)).thenReturn(Optional.of(allowedEmail));
    when(identityRepository.findUserByEmailNormalized("member@example.com"))
        .thenReturn(Optional.of(user(42L)));
    AuthSessionRevocationService revocationService = userId -> {
      throw new IllegalStateException("session store unavailable");
    };
    CountingMetrics metrics = new CountingMetrics();
    List<AdminOperationAuditEvent> audits = new ArrayList<>();
    BetaAccessAdminService service = service(
        repository,
        identityRepository,
        revocationService,
        audits,
        removalExecutor,
        metrics);

    BetaAllowedEmailRemovalResult result = service.removeEmail(7L, 1L);

    assertThat(result.sessionRevocationSucceeded()).isFalse();
    assertThat(result.associatedUserId()).isEqualTo(42L);
    assertThat(metrics.failures).isEqualTo(1);
    verify(removalExecutor).remove(7L);
    assertThat(audits).singleElement().satisfies(event -> {
      assertThat(event.action()).isEqualTo(AdminAuditAction.BETA_ALLOWED_EMAIL_REMOVE);
      assertThat(event.targetRef()).isEqualTo("7");
    });
  }

  private static BetaAccessAdminService service(
      BetaAccessRepository repository,
      IdentityUserRepository identityRepository,
      AuthSessionRevocationService revocationService,
      List<AdminOperationAuditEvent> audits,
      BetaAllowedEmailRemovalExecutor removalExecutor,
      BetaAccessMetrics metrics
  ) {
    return new BetaAccessAdminService(
        repository,
        identityRepository,
        revocationService,
        audits::add,
        removalExecutor,
        metrics,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private static AuthUser user(long id) {
    return new AuthUser(
        id,
        "member@example.com",
        "member@example.com",
        "Member",
        null,
        AuthUserStatus.ACTIVE,
        NOW,
        NOW,
        null,
        null,
        null);
  }

  private static final class CountingMetrics implements BetaAccessMetrics {
    private int failures;

    @Override
    public void recordSessionRevocationFailure() {
      failures++;
    }
  }
}
