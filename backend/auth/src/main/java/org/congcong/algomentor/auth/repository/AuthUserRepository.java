package org.congcong.algomentor.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUser;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.model.OAuthAccount;
import org.congcong.algomentor.auth.model.OAuthProvider;
import org.congcong.algomentor.auth.model.PasswordCredential;

public interface AuthUserRepository {

  Optional<OAuthAccount> findOAuthAccount(OAuthProvider provider, String providerSubject);

  Optional<AuthUser> findUserById(long userId);

  Optional<AuthUser> findUserByEmailNormalized(String emailNormalized);

  AuthUser createUser(
      String email,
      String emailNormalized,
      String displayName,
      String avatarUrl,
      AuthUserStatus status,
      Instant now);

  PasswordCredential createPasswordCredential(long userId, String passwordHash, Instant now);

  Optional<PasswordCredential> findPasswordCredentialByEmailNormalized(String emailNormalized);

  void addRole(long userId, AuthRole role);

  List<AuthRole> findRoles(long userId);

  OAuthAccount createOAuthAccount(OAuthAccount account);

  void updateOAuthAccountProfile(
      long accountId,
      String emailAtProvider,
      String displayNameAtProvider,
      String avatarUrlAtProvider,
      Instant updatedAt);

  AuthUser updateLastLoginAt(long userId, Instant lastLoginAt);
}
