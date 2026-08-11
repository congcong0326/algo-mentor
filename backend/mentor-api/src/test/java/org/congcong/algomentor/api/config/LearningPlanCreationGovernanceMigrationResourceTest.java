package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LearningPlanCreationGovernanceMigrationResourceTest {

  @Test
  void v60AddsDailyQuotaStorageAndCleanupIndexes() throws Exception {
    String sql = new ClassPathResource("db/migration/V60__learning_plan_creation_governance.sql")
        .getContentAsString(StandardCharsets.UTF_8);
    String mapper = new ClassPathResource("mapper/learningplan/LearningPlanMapper.xml")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(sql)
        .contains("CREATE TABLE IF NOT EXISTS learning_plan_daily_draft_usage")
        .contains("PRIMARY KEY (user_id, quota_date)")
        .contains("idx_learning_plan_draft_expiry");
    assertThat(mapper)
        .contains("ON CONFLICT (user_id, quota_date) DO UPDATE")
        .contains("draft_count &lt; EXCLUDED.applied_limit")
        .contains("FOR UPDATE SKIP LOCKED")
        .contains("pg_advisory_xact_lock")
        .contains("SELECT 1")
        .contains("learning-plan-total:");
  }
}
