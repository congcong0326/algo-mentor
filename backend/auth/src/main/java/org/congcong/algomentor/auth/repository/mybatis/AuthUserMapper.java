package org.congcong.algomentor.auth.repository.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.auth.repository.mybatis.model.AuthUserRow;
import org.congcong.algomentor.auth.repository.mybatis.model.OAuthAccountRow;

public interface AuthUserMapper {

  OAuthAccountRow findOAuthAccount(
      @Param("provider") String provider,
      @Param("providerSubject") String providerSubject);

  AuthUserRow findUserById(@Param("userId") long userId);

  AuthUserRow findUserByEmailNormalized(@Param("emailNormalized") String emailNormalized);

  int insertUser(AuthUserRow user);

  int insertUserRole(
      @Param("userId") long userId,
      @Param("role") String role,
      @Param("createdAt") Instant createdAt);

  List<String> findRoles(@Param("userId") long userId);

  int insertOAuthAccount(OAuthAccountRow account);

  int updateOAuthAccountProfile(
      @Param("accountId") long accountId,
      @Param("emailAtProvider") String emailAtProvider,
      @Param("displayNameAtProvider") String displayNameAtProvider,
      @Param("avatarUrlAtProvider") String avatarUrlAtProvider,
      @Param("updatedAt") Instant updatedAt);

  int updateLastLoginAt(
      @Param("userId") long userId,
      @Param("lastLoginAt") Instant lastLoginAt);
}
