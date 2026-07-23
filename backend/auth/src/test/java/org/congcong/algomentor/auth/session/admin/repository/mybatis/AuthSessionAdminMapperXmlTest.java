package org.congcong.algomentor.auth.session.admin.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminActivityFilter;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminQuery;
import org.congcong.algomentor.auth.session.admin.repository.mybatis.model.AuthSessionAdminRow;
import org.congcong.algomentor.auth.session.admin.repository.mybatis.model.AuthSessionAdminSummaryRow;
import org.junit.jupiter.api.Test;

class AuthSessionAdminMapperXmlTest {

  @Test
  void mybatisLoadsSessionAdministrationMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/auth/AuthSessionAdminMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/auth/AuthSessionAdminMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    String namespace = AuthSessionAdminMapper.class.getName() + ".";
    assertThat(configuration.hasStatement(namespace + "findPage")).isTrue();
    assertThat(configuration.hasStatement(namespace + "count")).isTrue();
    assertThat(configuration.hasStatement(namespace + "summary")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findValidBySessionRef")).isTrue();
    assertThat(configuration.getResultMap(namespace + "AuthSessionAdminRowMap")
        .getConstructorResultMappings()).hasSize(9);
  }

  @Test
  void numericKeywordMatchesUserIdAndIdentityFields() throws Exception {
    Configuration configuration = loadConfiguration();
    AuthSessionAdminQuery query = new AuthSessionAdminQuery(
        1,
        20,
        "42",
        42L,
        AuthSessionAdminActivityFilter.ALL);

    BoundSql boundSql = configuration.getMappedStatement(AuthSessionAdminMapper.class.getName() + ".findPage")
        .getBoundSql(Map.of(
            "query", query,
            "nowEpochMillis", Instant.parse("2026-07-23T09:26:00Z").toEpochMilli(),
            "activeSinceEpochMillis", Instant.parse("2026-07-23T09:21:00Z").toEpochMilli()));

    assertThat(boundSql.getSql())
        .contains("cast(s.PRINCIPAL_NAME as bigint) = ?")
        .contains("lower(coalesce(u.email_normalized, '')) like")
        .contains("lower(coalesce(u.display_name, '')) like");
  }

  @Test
  void rowModelsAcceptJdbcLongConstructorArguments() {
    DefaultObjectFactory objectFactory = new DefaultObjectFactory();

    AuthSessionAdminRow row = objectFactory.create(
        AuthSessionAdminRow.class,
        List.of(String.class, String.class, Long.class, Long.class, Long.class, Long.class,
            String.class, String.class, String.class),
        List.of("session-ref", "session-id", 1L, 2L, 3L, 4L, "user@example.com", "User", "ACTIVE"));
    AuthSessionAdminSummaryRow summary = objectFactory.create(
        AuthSessionAdminSummaryRow.class,
        List.of(Long.class, Long.class, Long.class),
        List.of(4L, 3L, 2L));

    assertThat(row.userId()).isEqualTo(1L);
    assertThat(row.expiryTime()).isEqualTo(4L);
    assertThat(summary.validSessionCount()).isEqualTo(4L);
  }

  private static Configuration loadConfiguration() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    try (Reader reader = Resources.getResourceAsReader("mapper/auth/AuthSessionAdminMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/auth/AuthSessionAdminMapper.xml",
          configuration.getSqlFragments()).parse();
    }
    return configuration;
  }
}
