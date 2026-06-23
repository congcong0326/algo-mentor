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
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.insertUser")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.findUserByEmailNormalized")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper.insertOAuthAccount")).isTrue();
  }
}
