package org.congcong.algomentor.ai.governance.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class AiGovernanceMigrationResourceTest {

  @Test
  void migrationDefinesAdmissionAndDailyUsageTables() throws IOException {
    Resource resource = new PathMatchingResourcePatternResolver()
        .getResource("classpath:db/migration/ai/V10__ai_governance_schema.sql");

    assertThat(resource.exists()).isTrue();
    String sql = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS ai_run_admissions");
    assertThat(sql).contains("CREATE TABLE IF NOT EXISTS ai_daily_usage");
    assertThat(sql).contains("UNIQUE (run_id)");
    assertThat(sql).contains("UNIQUE (user_id, quota_date, scope)");
  }
}
