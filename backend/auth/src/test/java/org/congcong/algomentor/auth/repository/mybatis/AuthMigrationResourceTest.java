package org.congcong.algomentor.auth.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AuthMigrationResourceTest {

  @Test
  void authMigrationDefinesAuthAndSpringSessionTables() throws Exception {
    ClassPathResource resource = new ClassPathResource("db/migration/auth/V8__auth_schema.sql");

    assertThat(resource.exists()).isTrue();

    String sql = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(sql)
        .contains("create table if not exists auth_users")
        .contains("create table if not exists auth_user_roles")
        .contains("create table if not exists auth_oauth_accounts")
        .contains("create table if not exists SPRING_SESSION")
        .contains("create table if not exists SPRING_SESSION_ATTRIBUTES");
  }

  @Test
  void passwordCredentialMigrationDefinesPasswordTable() throws Exception {
    ClassPathResource resource = new ClassPathResource("db/migration/auth/V14__auth_password_credentials.sql");

    assertThat(resource.exists()).isTrue();

    String sql = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(sql)
        .contains("create table if not exists auth_password_credentials")
        .contains("password_hash text not null")
        .contains("unique (user_id)");
  }
}
