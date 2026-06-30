package org.congcong.algomentor.identity.repository.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.model.IdentityUserPage;
import org.congcong.algomentor.identity.model.IdentityUserSearchQuery;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.congcong.algomentor.identity.repository.mybatis.model.AuthUserRow;

public class MyBatisIdentityUserRepository implements IdentityUserRepository {

  private final IdentityUserMapper mapper;

  public MyBatisIdentityUserRepository(IdentityUserMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<AuthUser> findUserById(long userId) {
    return Optional.ofNullable(mapper.findUserById(userId)).map(AuthUserRow::toDomain);
  }

  @Override
  public Optional<AuthUser> findUserByEmailNormalized(String emailNormalized) {
    if (emailNormalized == null || emailNormalized.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(mapper.findUserByEmailNormalized(emailNormalized)).map(AuthUserRow::toDomain);
  }

  @Override
  public AuthUser createUser(
      String email,
      String emailNormalized,
      String displayName,
      String avatarUrl,
      AuthUserStatus status,
      Instant now
  ) {
    AuthUserRow row = new AuthUserRow(
        null,
        email,
        emailNormalized,
        displayName,
        avatarUrl,
        status.name(),
        now,
        now,
        now,
        null,
        null);
    mapper.insertUser(row);
    return row.toDomain();
  }

  @Override
  public void addRole(long userId, AuthRole role) {
    mapper.insertUserRole(userId, role.name(), Instant.now());
  }

  @Override
  public List<AuthRole> findRoles(long userId) {
    return mapper.findRoles(userId)
        .stream()
        .map(AuthRole::valueOf)
        .toList();
  }

  @Override
  public AuthUser updateLastLoginAt(long userId, Instant lastLoginAt) {
    int updatedRows = mapper.updateLastLoginAt(userId, lastLoginAt);
    if (updatedRows != 1) {
      throw new IllegalStateException("Cannot update identity user login time: " + userId);
    }
    return findUserById(userId)
        .orElseThrow(() -> new IllegalStateException("Cannot load identity user after updating login time: " + userId));
  }

  @Override
  public IdentityUserPage searchUsers(IdentityUserSearchQuery query) {
    List<String> statuses = query.effectiveStatuses()
        .stream()
        .map(AuthUserStatus::name)
        .toList();
    List<AuthUser> users = mapper.searchUsers(query.keyword(), statuses, query.pageSize(), query.offset())
        .stream()
        .map(AuthUserRow::toDomain)
        .toList();
    long total = mapper.countUsers(query.keyword(), statuses);
    return new IdentityUserPage(users, total, query.page(), query.pageSize());
  }

  @Override
  public boolean updateUserStatus(long userId, AuthUserStatus expectedStatus, AuthUserStatus status, Instant updatedAt) {
    return mapper.updateUserStatus(userId, expectedStatus.name(), status.name(), updatedAt) == 1;
  }

  @Override
  public boolean softDeleteUser(long userId, long operatorUserId, AuthUserStatus expectedStatus, Instant deletedAt) {
    return mapper.softDeleteUser(userId, operatorUserId, expectedStatus.name(), deletedAt) == 1;
  }
}
