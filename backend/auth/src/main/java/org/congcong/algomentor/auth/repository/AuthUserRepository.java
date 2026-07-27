package org.congcong.algomentor.auth.repository;

import java.time.Instant;
import java.util.Optional;
import org.congcong.algomentor.auth.model.OAuthAccount;
import org.congcong.algomentor.auth.model.OAuthProvider;
import org.congcong.algomentor.auth.model.PasswordCredential;

public interface AuthUserRepository {

  Optional<OAuthAccount> findOAuthAccount(OAuthProvider provider, String providerSubject);

  PasswordCredential createPasswordCredential(long userId, String passwordHash, Instant now);

  Optional<PasswordCredential> findPasswordCredentialByEmailNormalized(String emailNormalized);

  default Optional<PasswordCredential> findPasswordCredentialByUserId(long userId) {
    return Optional.empty();
  }

  default boolean resetPasswordCredential(
      long userId,
      String passwordHash,
      Instant expiresAt,
      long resetBy,
      Instant updatedAt
  ) {
    return false;
  }

  default boolean consumeTemporaryPassword(
      long userId,
      String expectedPasswordHash,
      Instant consumedAt
  ) {
    return false;
  }

  default boolean completePasswordReset(long userId, String passwordHash, Instant changedAt) {
    return false;
  }

  default boolean updatePasswordCredentialCompareAndSet(
      long userId,
      String expectedPasswordHash,
      String newPasswordHash,
      Instant changedAt
  ) {
    return false;
  }

  default boolean insertPasswordCredentialIfAbsent(long userId, String passwordHash, Instant changedAt) {
    return false;
  }

  default boolean replacePasswordCredential(long userId, String passwordHash, Instant changedAt) {
    return false;
  }

  OAuthAccount createOAuthAccount(OAuthAccount account);

  void updateOAuthAccountProfile(
      long accountId,
      String emailAtProvider,
      String displayNameAtProvider,
      String avatarUrlAtProvider,
      Instant updatedAt);
}
