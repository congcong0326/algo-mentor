package org.congcong.algomentor.identity.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.model.IdentityUserPage;
import org.congcong.algomentor.identity.model.IdentityUserSearchQuery;

public interface IdentityUserRepository {

  Optional<AuthUser> findUserById(long userId);

  Optional<AuthUser> findUserByEmailNormalized(String emailNormalized);

  AuthUser createUser(
      String email,
      String emailNormalized,
      String displayName,
      String avatarUrl,
      AuthUserStatus status,
      Instant now);

  void addRole(long userId, AuthRole role);

  List<AuthRole> findRoles(long userId);

  AuthUser updateLastLoginAt(long userId, Instant lastLoginAt);

  IdentityUserPage searchUsers(IdentityUserSearchQuery query);

  boolean updateUserStatus(long userId, AuthUserStatus expectedStatus, AuthUserStatus status, Instant updatedAt);

  boolean softDeleteUser(long userId, long operatorUserId, AuthUserStatus expectedStatus, Instant deletedAt);
}
