package org.congcong.algomentor.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessErrorCode;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessException;
import org.congcong.algomentor.auth.betaaccess.service.BetaAccessPolicy;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.auth.passwordreset.PasswordResetErrorCode;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

class TemporaryPasswordConsumptionTest {

  private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

  @Test
  void staleConcurrentAuthenticationCanConsumeTemporaryPasswordOnlyOnce() {
    AtomicCredentialRepository repository = new AtomicCredentialRepository(credential(NOW.plusSeconds(3600), null));
    TestProvider provider = new TestProvider(repository);
    AuthenticatedUserDetails details = details(repository.credential.get());

    Authentication first = provider.success(details);

    assertThat(((AuthenticatedUserPrincipal) first.getPrincipal()).passwordChangeRequired()).isTrue();
    assertThatThrownBy(() -> provider.success(details))
        .isInstanceOf(TemporaryPasswordAuthenticationException.class)
        .extracting(exception -> ((TemporaryPasswordAuthenticationException) exception).code())
        .isEqualTo(PasswordResetErrorCode.AUTH_TEMPORARY_PASSWORD_CONSUMED);
  }

  @Test
  void expiredTemporaryPasswordIsRejectedWithoutConsumption() {
    AtomicCredentialRepository repository = new AtomicCredentialRepository(credential(NOW.minusSeconds(1), null));
    TestProvider provider = new TestProvider(repository);

    assertThatThrownBy(() -> provider.success(details(repository.credential.get())))
        .isInstanceOf(TemporaryPasswordAuthenticationException.class)
        .extracting(exception -> ((TemporaryPasswordAuthenticationException) exception).code())
        .isEqualTo(PasswordResetErrorCode.AUTH_TEMPORARY_PASSWORD_EXPIRED);
  }

  @Test
  void betaAccessDenialHappensBeforeTemporaryPasswordConsumption() {
    AtomicCredentialRepository repository = new AtomicCredentialRepository(credential(NOW.plusSeconds(3600), null));
    BetaAccessPolicy policy = mock(BetaAccessPolicy.class);
    doThrow(new BetaAccessException(BetaAccessErrorCode.AUTH_BETA_ACCESS_DENIED, "denied"))
        .when(policy).requireAllowed("member@example.com", false);
    TestProvider provider = new TestProvider(repository, policy);

    assertThatThrownBy(() -> provider.success(details(repository.credential.get())))
        .isInstanceOf(BetaAccessAuthenticationException.class);
    assertThat(repository.credential.get().temporaryPasswordConsumedAt()).isNull();
  }

  private static AuthenticatedUserDetails details(PasswordCredential credential) {
    return new AuthenticatedUserDetails(
        new AuthenticatedUserPrincipal(
            42L,
            "member@example.com",
            "Member",
            null,
            List.of(AuthRole.USER),
            AuthUserStatus.ACTIVE),
        credential,
        AuthAuthorities.fromRoles(List.of(AuthRole.USER)));
  }

  private static PasswordCredential credential(Instant expiresAt, Instant consumedAt) {
    return new PasswordCredential(
        1L,
        42L,
        "temporary-hash",
        true,
        expiresAt,
        consumedAt,
        null,
        1L,
        NOW,
        NOW);
  }

  private static final class TestProvider extends AuthenticatedDaoAuthenticationProvider {
    private TestProvider(AuthUserRepository repository) {
      this(repository, null);
    }

    private TestProvider(AuthUserRepository repository, BetaAccessPolicy betaAccessPolicy) {
      super(
          NoOpPasswordEncoder.getInstance(),
          mock(PasswordUserDetailsService.class),
          repository,
          Clock.fixed(NOW, ZoneOffset.UTC),
          betaAccessPolicy);
    }

    private Authentication success(AuthenticatedUserDetails details) {
      return createSuccessAuthentication(
          details.principal(),
          UsernamePasswordAuthenticationToken.unauthenticated("member@example.com", "temporary"),
          details);
    }
  }

  private static final class AtomicCredentialRepository implements AuthUserRepository {
    private final AtomicReference<PasswordCredential> credential;

    private AtomicCredentialRepository(PasswordCredential credential) {
      this.credential = new AtomicReference<>(credential);
    }

    @Override
    public Optional<PasswordCredential> findPasswordCredentialByUserId(long userId) {
      return Optional.of(credential.get());
    }

    @Override
    public boolean consumeTemporaryPassword(long userId, String expectedPasswordHash, Instant consumedAt) {
      while (true) {
        PasswordCredential current = credential.get();
        if (current.temporaryPasswordConsumedAt() != null
            || !current.temporaryPasswordExpiresAt().isAfter(consumedAt)
            || !current.passwordHash().equals(expectedPasswordHash)) {
          return false;
        }
        PasswordCredential consumed = new PasswordCredential(
            current.id(),
            current.userId(),
            current.passwordHash(),
            true,
            current.temporaryPasswordExpiresAt(),
            consumedAt,
            current.passwordChangedAt(),
            current.resetBy(),
            current.createdAt(),
            consumedAt);
        if (credential.compareAndSet(current, consumed)) {
          return true;
        }
      }
    }

    @Override
    public Optional<org.congcong.algomentor.auth.model.OAuthAccount> findOAuthAccount(
        org.congcong.algomentor.auth.model.OAuthProvider provider,
        String providerSubject
    ) {
      return Optional.empty();
    }

    @Override
    public PasswordCredential createPasswordCredential(long userId, String passwordHash, Instant now) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<PasswordCredential> findPasswordCredentialByEmailNormalized(String emailNormalized) {
      return Optional.empty();
    }

    @Override
    public org.congcong.algomentor.auth.model.OAuthAccount createOAuthAccount(
        org.congcong.algomentor.auth.model.OAuthAccount account
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void updateOAuthAccountProfile(
        long accountId,
        String emailAtProvider,
        String displayNameAtProvider,
        String avatarUrlAtProvider,
        Instant updatedAt
    ) {
    }
  }
}
