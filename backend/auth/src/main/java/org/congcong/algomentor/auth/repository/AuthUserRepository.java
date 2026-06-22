package org.congcong.algomentor.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUser;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.model.OAuthAccount;
import org.congcong.algomentor.auth.model.OAuthProvider;

public interface AuthUserRepository {

  Optional<OAuthAccount> findOAuthAccount(OAuthProvider provider, String providerSubject);

  Optional<AuthUser> findUserById(long userId);

  AuthUser createUser(
      String email,
      String emailNormalized,
      String displayName,
      String avatarUrl,
      AuthUserStatus status,
      Instant now);

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
