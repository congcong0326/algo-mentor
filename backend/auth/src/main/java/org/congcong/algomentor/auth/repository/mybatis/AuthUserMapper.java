package org.congcong.algomentor.auth.repository.mybatis;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.auth.repository.mybatis.model.OAuthAccountRow;
import org.congcong.algomentor.auth.repository.mybatis.model.PasswordCredentialRow;

public interface AuthUserMapper {

  OAuthAccountRow findOAuthAccount(
      @Param("provider") String provider,
      @Param("providerSubject") String providerSubject);

  int insertPasswordCredential(PasswordCredentialRow credential);

  PasswordCredentialRow findPasswordCredentialByEmailNormalized(@Param("emailNormalized") String emailNormalized);

  PasswordCredentialRow findPasswordCredentialByUserId(@Param("userId") long userId);

  int resetPasswordCredential(
      @Param("userId") long userId,
      @Param("passwordHash") String passwordHash,
      @Param("expiresAt") Instant expiresAt,
      @Param("resetBy") long resetBy,
      @Param("updatedAt") Instant updatedAt);

  int consumeTemporaryPassword(
      @Param("userId") long userId,
      @Param("expectedPasswordHash") String expectedPasswordHash,
      @Param("consumedAt") Instant consumedAt);

  int completePasswordReset(
      @Param("userId") long userId,
      @Param("passwordHash") String passwordHash,
      @Param("changedAt") Instant changedAt);

  int insertOAuthAccount(OAuthAccountRow account);

  int updateOAuthAccountProfile(
      @Param("accountId") long accountId,
      @Param("emailAtProvider") String emailAtProvider,
      @Param("displayNameAtProvider") String displayNameAtProvider,
      @Param("avatarUrlAtProvider") String avatarUrlAtProvider,
      @Param("updatedAt") Instant updatedAt);
}
