package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class InternalBetaMigrationResourceTest {

  @Test
  void internalBetaMigrationsRemainDiscoverableThroughUsageAccountingHardening() {
    assertThat(new ClassPathResource("db/migration/auth/V28__beta_access_and_password_reset.sql").exists()).isTrue();
    assertThat(new ClassPathResource("db/migration/ai/V29__ai_runtime_policy_and_model_price.sql").exists()).isTrue();
    assertThat(new ClassPathResource("db/migration/V30__admin_audit_and_user_feedback.sql").exists()).isTrue();
    assertThat(new ClassPathResource("db/migration/agent/V31__agent_diagnostic_retention.sql").exists()).isTrue();
    assertThat(new ClassPathResource("db/migration/ai/V32__ai_usage_accounting_hardening.sql").exists()).isTrue();
  }

  @Test
  void adminAuditMigrationKeepsSensitiveContentOutOfSchema() throws Exception {
    ClassPathResource resource = new ClassPathResource(
        "db/migration/V30__admin_audit_and_user_feedback.sql");

    assertThat(resource.exists()).isTrue();
    assertThat(resource.getContentAsString(StandardCharsets.UTF_8))
        .contains("CREATE TABLE admin_operation_audit")
        .contains("metadata_json JSONB")
        .contains("CREATE TABLE user_feedback_thread")
        .contains("CREATE TABLE user_feedback_message")
        .doesNotContain("temporary_password", "authorization", "prompt", "response_json");
  }
}
