package org.congcong.algomentor.agent.persistence.postgres.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AgentDiagnosticRetentionMigrationTest {

  @Test
  void migrationBackfillsThirtyDayRetentionForExistingRuns() throws Exception {
    ClassPathResource resource = new ClassPathResource(
        "db/migration/agent/V31__agent_diagnostic_retention.sql");

    assertThat(resource.exists()).isTrue();
    assertThat(resource.getContentAsString(StandardCharsets.UTF_8))
        .contains("ADD COLUMN diagnostic_retention_expires_at")
        .contains("COALESCE(ended_at, started_at) + INTERVAL '30 days'")
        .contains("WHERE diagnostic_redacted_at IS NULL");
  }

  @Test
  void runtimeAuditMigrationAddsParentAndTriggerConstraints() throws Exception {
    ClassPathResource resource = new ClassPathResource(
        "db/migration/agent/V45__agent_runtime_run_audit.sql");

    assertThat(resource.exists()).isTrue();
    assertThat(resource.getContentAsString(StandardCharsets.UTF_8))
        .contains("ADD COLUMN agent_key")
        .contains("ADD COLUMN parent_step_index")
        .contains("parent_step_index > 0")
        .contains("USER_ENTRY", "CHILD", "BACKGROUND")
        .contains("idx_agent_run_parent_run_step");
  }
}
