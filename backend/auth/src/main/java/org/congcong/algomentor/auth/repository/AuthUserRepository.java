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

  OAuthAccount createOAuthAccount(OAuthAccount account);

  void updateOAuthAccountProfile(
      long accountId,
      String emailAtProvider,
      String displayNameAtProvider,
      String avatarUrlAtProvider,
      Instant updatedAt);
}
