package org.congcong.algomentor.auth.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.congcong.algomentor.auth.model.OAuthAccount;
import org.congcong.algomentor.auth.model.OAuthProvider;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.auth.repository.mybatis.model.OAuthAccountRow;
import org.congcong.algomentor.auth.repository.mybatis.model.PasswordCredentialRow;
import org.junit.jupiter.api.Test;

class MyBatisAuthUserRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-06-23T00:00:00Z");

  private final GeneratedIdMapper mapper = new GeneratedIdMapper();
  private final MyBatisAuthUserRepository repository = new MyBatisAuthUserRepository(mapper);

  @Test
  void createOAuthAccountReturnsGeneratedIdFromMapper() {
    OAuthAccount account = repository.createOAuthAccount(new OAuthAccount(
        null,
        42L,
        OAuthProvider.GOOGLE,
        "google-sub",
        "user@example.com",
        "User Name",
        "https://example.com/avatar.png",
        NOW,
        NOW));

    assertThat(account.id()).isEqualTo(99L);
    assertThat(account.userId()).isEqualTo(42L);
    assertThat(account.provider()).isEqualTo(OAuthProvider.GOOGLE);
  }

  @Test
  void createPasswordCredentialReturnsGeneratedIdFromMapper() {
    PasswordCredential credential = repository.createPasswordCredential(42L, "{bcrypt}hash", NOW);

    assertThat(credential.id()).isEqualTo(77L);
    assertThat(credential.userId()).isEqualTo(42L);
    assertThat(credential.passwordHash()).isEqualTo("{bcrypt}hash");
  }

  private static final class GeneratedIdMapper implements AuthUserMapper {

    @Override
    public OAuthAccountRow findOAuthAccount(String provider, String providerSubject) {
      return null;
    }

    @Override
    public int insertPasswordCredential(PasswordCredentialRow credential) {
      credential.setId(77L);
      return 1;
    }

    @Override
    public PasswordCredentialRow findPasswordCredentialByEmailNormalized(String emailNormalized) {
      return null;
    }

    @Override
    public PasswordCredentialRow findPasswordCredentialByUserId(long userId) {
      return null;
    }

    @Override
    public int resetPasswordCredential(
        long userId,
        String passwordHash,
        Instant expiresAt,
        long resetBy,
        Instant updatedAt
    ) {
      return 0;
    }

    @Override
    public int consumeTemporaryPassword(long userId, String expectedPasswordHash, Instant consumedAt) {
      return 0;
    }

    @Override
    public int completePasswordReset(long userId, String passwordHash, Instant changedAt) {
      return 0;
    }

    @Override
    public int updatePasswordCredentialCompareAndSet(
        long userId,
        String expectedPasswordHash,
        String newPasswordHash,
        Instant changedAt
    ) {
      return 0;
    }

    @Override
    public int insertPasswordCredentialIfAbsent(long userId, String passwordHash, Instant changedAt) {
      return 0;
    }

    @Override
    public int replacePasswordCredential(long userId, String passwordHash, Instant changedAt) {
      return 0;
    }

    @Override
    public int insertOAuthAccount(OAuthAccountRow account) {
      account.setId(99L);
      return 1;
    }

    @Override
    public int updateOAuthAccountProfile(
        long accountId,
        String emailAtProvider,
        String displayNameAtProvider,
        String avatarUrlAtProvider,
        Instant updatedAt
    ) {
      return 1;
    }
  }
}
