package org.congcong.algomentor.auth.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AuthUserMapperXmlTest {

  @Test
  void mybatisLoadsAuthMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/auth/AuthUserMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/auth/AuthUserMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.findOAuthAccount")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.insertUser")).isFalse();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.findUserByEmailNormalized")).isFalse();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.insertPasswordCredential")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.findPasswordCredentialByEmailNormalized")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.findRoles")).isFalse();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.insertOAuthAccount")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.consumeTemporaryPassword")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.completePasswordReset")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.updatePasswordCredentialCompareAndSet")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.insertPasswordCredentialIfAbsent")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.replacePasswordCredential")).isTrue();
    assertThat(configuration.getResultMap(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.PasswordCredentialRowMap")
        .getConstructorResultMappings().get(3).getJavaType()).isEqualTo(boolean.class);
  }

  @Test
  void mybatisLoadsBetaAccessMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/auth/BetaAccessMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/auth/BetaAccessMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    String namespace = "org.congcong.algomentor.auth.betaaccess.repository.mybatis.BetaAccessMapper.";
    assertThat(configuration.hasStatement(namespace + "findSettings")).isTrue();
    assertThat(configuration.hasStatement(namespace + "insertAllowedEmail")).isTrue();
    assertThat(configuration.hasStatement(namespace + "deleteAllowedEmail")).isTrue();
    var settingsMappings = configuration.getResultMap(namespace + "BetaAccessSettingsRowMap")
        .getConstructorResultMappings();
    assertThat(settingsMappings.get(0).getJavaType()).isEqualTo(short.class);
    assertThat(settingsMappings.get(1).getJavaType()).isEqualTo(boolean.class);
    var allowedEmailMappings = configuration.getResultMap(namespace + "BetaAllowedEmailRowMap")
        .getConstructorResultMappings();
    assertThat(allowedEmailMappings.get(0).getJavaType()).isEqualTo(long.class);
    assertThat(allowedEmailMappings.get(3).getJavaType()).isEqualTo(long.class);
  }

  @Test
  void mybatisLoadsAuthLoginSettingsMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/auth/AuthLoginSettingsMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/auth/AuthLoginSettingsMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    String namespace = "org.congcong.algomentor.auth.loginsettings.repository.mybatis.AuthLoginSettingsMapper.";
    assertThat(configuration.hasStatement(namespace + "findSettings")).isTrue();
    assertThat(configuration.hasStatement(namespace + "insertIfAbsent")).isTrue();
    assertThat(configuration.hasStatement(namespace + "updateSettings")).isTrue();
    assertThat(configuration.getResultMap(namespace + "AuthLoginSettingsRowMap")
        .getConstructorResultMappings()).hasSize(9);
  }
}
