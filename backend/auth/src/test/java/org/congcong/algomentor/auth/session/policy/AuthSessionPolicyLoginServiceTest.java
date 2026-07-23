package org.congcong.algomentor.auth.session.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.session.AuthSessionRecord;
import org.congcong.algomentor.auth.session.AuthSessionRepository;
import org.congcong.algomentor.auth.session.AuthSessionRevocationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class AuthSessionPolicyLoginServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");

  @Test
  void snapshotsPolicyAndEvictsOldestValidOtherSession() {
    AuthSessionPolicyResolver resolver = mock(AuthSessionPolicyResolver.class);
    when(resolver.resolve(42L)).thenReturn(new ResolvedUserSessionPolicy(
        new UserSessionPolicy(2, 3_600L), 7L, 3L));
    FakeSessionRepository sessions = new FakeSessionRepository(List.of(
        session("old", NOW.minusSeconds(90), false),
        session("newer", NOW.minusSeconds(30), false),
        session("expired", NOW.minusSeconds(120), true)));
    FakeRevocationService revocation = new FakeRevocationService();
    MockHttpSession currentSession = new MockHttpSession(null, "current");

    service(resolver, sessions, revocation).apply(42L, currentSession);

    assertThat(currentSession.getAttribute(AuthSessionAttributeNames.ABSOLUTE_EXPIRES_AT_EPOCH_MILLIS))
        .isEqualTo(NOW.plusSeconds(3_600).toEpochMilli());
    assertThat(currentSession.getAttribute(AuthSessionAttributeNames.POLICY_ID)).isEqualTo(7L);
    assertThat(currentSession.getAttribute(AuthSessionAttributeNames.POLICY_VERSION)).isEqualTo(3L);
    assertThat(currentSession.getMaxInactiveInterval()).isEqualTo(3_600);
    assertThat(revocation.revokedSessionIds).containsExactly("old");
  }

  @Test
  void usesSessionIdAsStableTieBreakerWhenCreationTimesMatch() {
    AuthSessionPolicyResolver resolver = mock(AuthSessionPolicyResolver.class);
    when(resolver.resolve(42L)).thenReturn(new ResolvedUserSessionPolicy(
        new UserSessionPolicy(1, 3_600L), null, null));
    Instant sameCreationTime = NOW.minusSeconds(60);
    FakeRevocationService revocation = new FakeRevocationService();

    service(
        resolver,
        new FakeSessionRepository(List.of(
            session("same-b", sameCreationTime, false),
            session("same-a", sameCreationTime, false))),
        revocation).apply(42L, new MockHttpSession(null, "current"));

    assertThat(revocation.revokedSessionIds).containsExactly("same-a", "same-b");
  }

  @Test
  void rejectsLoginAndInvalidatesCurrentSessionWhenEvictionFails() {
    AuthSessionPolicyResolver resolver = mock(AuthSessionPolicyResolver.class);
    when(resolver.resolve(42L)).thenReturn(new ResolvedUserSessionPolicy(
        new UserSessionPolicy(1, 3_600L), null, null));
    FakeRevocationService revocation = new FakeRevocationService();
    revocation.nextResult = false;
    MockHttpSession currentSession = new MockHttpSession(null, "current");

    assertThatThrownBy(() -> service(
        resolver,
        new FakeSessionRepository(List.of(session("old", NOW.minusSeconds(60), false))),
        revocation).apply(42L, currentSession))
        .isInstanceOf(AuthSessionPolicyException.class)
        .extracting(exception -> ((AuthSessionPolicyException) exception).code())
        .isEqualTo(AuthSessionPolicyErrorCode.AUTH_SESSION_POLICY_UNAVAILABLE);
    assertThat(currentSession.isInvalid()).isTrue();
  }

  private static AuthSessionPolicyLoginService service(
      AuthSessionPolicyResolver resolver,
      AuthSessionRepository sessions,
      AuthSessionRevocationService revocation
  ) {
    return new AuthSessionPolicyLoginService(
        resolver,
        sessions,
        revocation,
        new NoopAuthSessionPolicyMetrics(),
        Clock.fixed(NOW, ZoneOffset.UTC),
        new AuthProperties());
  }

  private static AuthSessionRecord session(String id, Instant creationTime, boolean expired) {
    return new AuthSessionRecord(id, creationTime, expired);
  }

  private record FakeSessionRepository(List<AuthSessionRecord> sessions) implements AuthSessionRepository {

    @Override
    public List<AuthSessionRecord> findByUserId(long userId) {
      return sessions;
    }
  }

  private static final class FakeRevocationService implements AuthSessionRevocationService {

    private final List<String> revokedSessionIds = new ArrayList<>();
    private boolean nextResult = true;

    @Override
    public boolean revokeSession(String sessionId) {
      revokedSessionIds.add(sessionId);
      return nextResult;
    }

    @Override
    public int revokeSessionsForUser(long userId) {
      return 0;
    }
  }
}
