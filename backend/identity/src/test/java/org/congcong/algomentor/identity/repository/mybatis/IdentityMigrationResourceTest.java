package org.congcong.algomentor.identity.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class IdentityMigrationResourceTest {

  @Test
  void identityMigrationAddsSoftDeleteColumnsAndIndexes() throws Exception {
    ClassPathResource resource = new ClassPathResource(
        "db/migration/identity/V16__identity_user_soft_delete.sql");

    assertThat(resource.exists()).isTrue();

    String sql = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(sql)
        .contains("alter table auth_users")
        .contains("deleted_at timestamptz")
        .contains("deleted_by bigint")
        .contains("idx_auth_users_status")
        .contains("idx_auth_users_deleted_at")
        .contains("idx_auth_users_email_display_name");
  }
}
