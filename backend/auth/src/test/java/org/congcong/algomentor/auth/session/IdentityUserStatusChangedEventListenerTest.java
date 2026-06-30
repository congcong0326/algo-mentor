package org.congcong.algomentor.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.identity.event.IdentityUserStatusChangedEvent;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.Test;

class IdentityUserStatusChangedEventListenerTest {

  private static final Instant NOW = Instant.parse("2026-06-30T00:00:00Z");

  private final FakeAuthSessionRevocationService revocationService = new FakeAuthSessionRevocationService();
  private final FakeAuthSessionMetrics metrics = new FakeAuthSessionMetrics();
  private final IdentityUserStatusChangedEventListener listener =
      new IdentityUserStatusChangedEventListener(revocationService, metrics);

  @Test
  void disabledEventRevokesTargetUserSessions() {
    listener.onStatusChanged(new IdentityUserStatusChangedEvent(
        42L,
        AuthUserStatus.ACTIVE,
        AuthUserStatus.DISABLED,
        7L,
        NOW));

    assertThat(revocationService.revokedUserIds).containsExactly(42L);
    assertThat(metrics.successCount).isEqualTo(1);
  }

  @Test
  void deletedEventRevokesTargetUserSessions() {
    listener.onStatusChanged(new IdentityUserStatusChangedEvent(
        42L,
        AuthUserStatus.ACTIVE,
        AuthUserStatus.DELETED,
        7L,
        NOW));

    assertThat(revocationService.revokedUserIds).containsExactly(42L);
    assertThat(metrics.successStatuses).containsExactly(AuthUserStatus.DELETED);
  }

  @Test
  void activeEventDoesNotRevokeSessions() {
    listener.onStatusChanged(new IdentityUserStatusChangedEvent(
        42L,
        AuthUserStatus.DISABLED,
        AuthUserStatus.ACTIVE,
        7L,
        NOW));

    assertThat(revocationService.revokedUserIds).isEmpty();
    assertThat(metrics.successCount).isZero();
    assertThat(metrics.failureCount).isZero();
  }

  @Test
  void revocationFailureIsRecordedAndDoesNotRethrow() {
    revocationService.failure = new IllegalStateException("session store unavailable");

    assertThatCode(() -> listener.onStatusChanged(new IdentityUserStatusChangedEvent(
        42L,
        AuthUserStatus.ACTIVE,
        AuthUserStatus.DISABLED,
        7L,
        NOW))).doesNotThrowAnyException();

    assertThat(revocationService.revokedUserIds).containsExactly(42L);
    assertThat(metrics.failureCount).isEqualTo(1);
    assertThat(metrics.failureStatuses).containsExactly(AuthUserStatus.DISABLED);
    assertThat(metrics.successCount).isZero();
  }

  private static final class FakeAuthSessionRevocationService implements AuthSessionRevocationService {

    private final List<Long> revokedUserIds = new ArrayList<>();
    private RuntimeException failure;

    @Override
    public int revokeSessionsForUser(long userId) {
      revokedUserIds.add(userId);
      if (failure != null) {
        throw failure;
      }
      return 2;
    }
  }

  private static final class FakeAuthSessionMetrics implements AuthSessionMetrics {

    private int successCount;
    private int failureCount;
    private final List<AuthUserStatus> successStatuses = new ArrayList<>();
    private final List<AuthUserStatus> failureStatuses = new ArrayList<>();

    @Override
    public void recordSuccess(AuthUserStatus status, int revokedSessions) {
      successCount++;
      successStatuses.add(status);
    }

    @Override
    public void recordFailure(AuthUserStatus status) {
      failureCount++;
      failureStatuses.add(status);
    }
  }
}
