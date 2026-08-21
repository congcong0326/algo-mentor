package org.congcong.algomentor.auth.loginsettings.repository.mybatis;

import java.time.Instant;
import org.apache.ibatis.annotations.Param;
import org.congcong.algomentor.auth.loginsettings.repository.mybatis.model.AuthLoginSettingsRow;

public interface AuthLoginSettingsMapper {

  AuthLoginSettingsRow findSettings();

  int insertIfAbsent(
      @Param("accountRegistrationEnabled") boolean accountRegistrationEnabled,
      @Param("passwordLoginEnabled") boolean passwordLoginEnabled,
      @Param("passwordRegistrationEnabled") boolean passwordRegistrationEnabled,
      @Param("googleLoginEnabled") boolean googleLoginEnabled,
      @Param("githubLoginEnabled") boolean githubLoginEnabled,
      @Param("updatedAt") Instant updatedAt);

  int updateSettings(
      @Param("accountRegistrationEnabled") boolean accountRegistrationEnabled,
      @Param("passwordLoginEnabled") boolean passwordLoginEnabled,
      @Param("passwordRegistrationEnabled") boolean passwordRegistrationEnabled,
      @Param("googleLoginEnabled") boolean googleLoginEnabled,
      @Param("githubLoginEnabled") boolean githubLoginEnabled,
      @Param("updatedBy") long updatedBy,
      @Param("updatedAt") Instant updatedAt);
}
