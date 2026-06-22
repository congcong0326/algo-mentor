package org.congcong.algomentor.auth.repository.mybatis;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUser;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.model.OAuthAccount;
import org.congcong.algomentor.auth.model.OAuthProvider;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.repository.mybatis.model.AuthUserRow;
import org.congcong.algomentor.auth.repository.mybatis.model.OAuthAccountRow;

public class MyBatisAuthUserRepository implements AuthUserRepository {

  private final AuthUserMapper mapper;

  public MyBatisAuthUserRepository(AuthUserMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<OAuthAccount> findOAuthAccount(OAuthProvider provider, String providerSubject) {
    return Optional.ofNullable(mapper.findOAuthAccount(provider.value(), providerSubject))
        .map(OAuthAccountRow::toDomain);
  }

  @Override
  public Optional<AuthUser> findUserById(long userId) {
    return Optional.ofNullable(mapper.findUserById(userId)).map(AuthUserRow::toDomain);
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
        now);
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
  public OAuthAccount createOAuthAccount(OAuthAccount account) {
    OAuthAccountRow row = new OAuthAccountRow(
        null,
        account.userId(),
        account.provider().value(),
        account.providerSubject(),
        account.emailAtProvider(),
        account.displayNameAtProvider(),
        account.avatarUrlAtProvider(),
        account.createdAt(),
        account.updatedAt());
    mapper.insertOAuthAccount(row);
    return row.toDomain();
  }

  @Override
  public void updateOAuthAccountProfile(
      long accountId,
      String emailAtProvider,
      String displayNameAtProvider,
      String avatarUrlAtProvider,
      Instant updatedAt
  ) {
    mapper.updateOAuthAccountProfile(accountId, emailAtProvider, displayNameAtProvider, avatarUrlAtProvider, updatedAt);
  }

  @Override
  public AuthUser updateLastLoginAt(long userId, Instant lastLoginAt) {
    mapper.updateLastLoginAt(userId, lastLoginAt);
    return findUserById(userId)
        .orElseThrow(() -> new IllegalStateException("Cannot load auth user after updating login time: " + userId));
  }
}
