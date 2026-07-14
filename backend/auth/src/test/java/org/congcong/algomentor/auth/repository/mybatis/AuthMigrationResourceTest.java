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

  @Test
  void betaAccessMigrationDefinesAllowlistAndTemporaryPasswordState() throws Exception {
    ClassPathResource resource = new ClassPathResource(
        "db/migration/auth/V28__beta_access_and_password_reset.sql");

    assertThat(resource.exists()).isTrue();
    assertThat(resource.getContentAsString(StandardCharsets.UTF_8))
        .contains("CREATE TABLE auth_beta_access_settings")
        .contains("CREATE TABLE auth_beta_allowed_email")
        .contains("email_normalized VARCHAR(320) NOT NULL")
        .contains("ADD COLUMN reset_required BOOLEAN NOT NULL DEFAULT FALSE")
        .contains("ADD COLUMN temporary_password_expires_at TIMESTAMPTZ NULL");
  }
}
