package org.congcong.algomentor.identity.group.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class UserGroupMigrationResourceTest {

  @Test
  void migrationDefinesGroupsMembershipsConstraintsAndIndexes() throws Exception {
    ClassPathResource resource = new ClassPathResource(
        "db/migration/identity/V38__identity_user_group.sql");

    assertThat(resource.exists()).isTrue();
    assertThat(resource.getContentAsString(StandardCharsets.UTF_8))
        .contains("CREATE TABLE identity_user_group")
        .contains("CREATE TABLE identity_user_group_membership")
        .contains("uk_identity_user_group_code")
        .contains("ck_identity_user_group_deleted_fields")
        .contains("expires_at IS NULL OR expires_at > joined_at")
        .contains("idx_identity_user_group_membership_group")
        .contains("idx_identity_user_group_membership_user_expiry")
        .contains("ON DELETE RESTRICT");
  }
}
