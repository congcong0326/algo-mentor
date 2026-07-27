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

  @Test
  void futureRuntimePolicyMigrationDefinesCallLevelUsageWithoutLegacyBackfill() throws IOException {
    Resource resource = new PathMatchingResourcePatternResolver()
        .getResource("classpath:db/migration/ai/V29__ai_runtime_policy_and_model_price.sql");

    assertThat(resource.exists()).isTrue();
    String sql = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(sql)
        .contains("CREATE TABLE ai_runtime_settings")
        .contains("CREATE TABLE ai_user_policy")
        .contains("CREATE TABLE ai_model_price")
        .contains("CREATE TABLE ai_llm_call_usage")
        .doesNotContain("INSERT INTO ai_llm_call_usage");
  }

  @Test
  void usageAccountingHardeningMigrationAddsConstraintsAndLegacyBackfill() throws IOException {
    Resource resource = new PathMatchingResourcePatternResolver()
        .getResource("classpath:db/migration/ai/V32__ai_usage_accounting_hardening.sql");

    assertThat(resource.exists()).isTrue();
    String sql = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(sql)
        .contains("ck_ai_llm_call_usage_input_tokens_non_negative")
        .contains("ck_ai_llm_call_usage_step_index_positive")
        .contains("ck_ai_model_price_provider_lower")
        .contains("legacy-run-")
        .contains("ON CONFLICT (call_id) DO NOTHING");
  }

  @Test
  void providerAndModelMigrationDefinesConfigurationResourcesAndNullableUsageLinks() throws IOException {
    Resource resource = new PathMatchingResourcePatternResolver()
        .getResource("classpath:db/migration/ai/V41__ai_provider_instance_and_model.sql");

    assertThat(resource.exists()).isTrue();
    String sql = resource.getContentAsString(StandardCharsets.UTF_8);
    assertThat(sql)
        .contains("CREATE TABLE ai_provider_instance")
        .contains("CREATE TABLE ai_model")
        .contains("jsonb_typeof(config) = 'object'")
        .contains("ADD COLUMN provider_instance_id BIGINT NULL")
        .contains("ADD COLUMN ai_model_id BIGINT NULL")
        .contains("ON DELETE RESTRICT");
  }
}
