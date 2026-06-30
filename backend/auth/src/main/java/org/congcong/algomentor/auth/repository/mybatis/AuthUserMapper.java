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

  int insertOAuthAccount(OAuthAccountRow account);

  int updateOAuthAccountProfile(
      @Param("accountId") long accountId,
      @Param("emailAtProvider") String emailAtProvider,
      @Param("displayNameAtProvider") String displayNameAtProvider,
      @Param("avatarUrlAtProvider") String avatarUrlAtProvider,
      @Param("updatedAt") Instant updatedAt);
}
