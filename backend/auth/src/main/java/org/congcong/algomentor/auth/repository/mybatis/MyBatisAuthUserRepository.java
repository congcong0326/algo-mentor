package org.congcong.algomentor.auth.repository.mybatis;

import java.time.Instant;
import java.util.Optional;
import org.congcong.algomentor.auth.model.OAuthAccount;
import org.congcong.algomentor.auth.model.OAuthProvider;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.repository.mybatis.model.OAuthAccountRow;
import org.congcong.algomentor.auth.repository.mybatis.model.PasswordCredentialRow;

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
  public PasswordCredential createPasswordCredential(long userId, String passwordHash, Instant now) {
    PasswordCredentialRow row = new PasswordCredentialRow(null, userId, passwordHash, now, now);
    mapper.insertPasswordCredential(row);
    return row.toDomain();
  }

  @Override
  public Optional<PasswordCredential> findPasswordCredentialByEmailNormalized(String emailNormalized) {
    if (emailNormalized == null || emailNormalized.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(mapper.findPasswordCredentialByEmailNormalized(emailNormalized))
        .map(PasswordCredentialRow::toDomain);
  }

  @Override
  public Optional<PasswordCredential> findPasswordCredentialByUserId(long userId) {
    if (userId < 1) {
      return Optional.empty();
    }
    return Optional.ofNullable(mapper.findPasswordCredentialByUserId(userId))
        .map(PasswordCredentialRow::toDomain);
  }

  @Override
  public boolean resetPasswordCredential(
      long userId,
      String passwordHash,
      Instant expiresAt,
      long resetBy,
      Instant updatedAt
  ) {
    return mapper.resetPasswordCredential(userId, passwordHash, expiresAt, resetBy, updatedAt) == 1;
  }

  @Override
  public boolean consumeTemporaryPassword(
      long userId,
      String expectedPasswordHash,
      Instant consumedAt
  ) {
    return mapper.consumeTemporaryPassword(userId, expectedPasswordHash, consumedAt) == 1;
  }

  @Override
  public boolean completePasswordReset(long userId, String passwordHash, Instant changedAt) {
    return mapper.completePasswordReset(userId, passwordHash, changedAt) == 1;
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
}
