package org.congcong.algomentor.api.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class AiProviderModelMigrationIT extends PostgresIntegrationTestSupport {

  @Test
  void migratesProviderModelResourcesAndKeepsHistoricUsageLinksNullable() throws Exception {
    migrateLatest();

    assertThat(queryLong("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '41' AND success = TRUE"))
        .isEqualTo(1L);
    assertThat(queryString("SELECT to_regclass('ai_provider_instance')"))
        .isEqualTo("ai_provider_instance");
    assertThat(queryString("SELECT to_regclass('ai_model')")).isEqualTo("ai_model");
    assertThat(nullable("ai_llm_call_usage", "provider_instance_id")).isTrue();
    assertThat(nullable("ai_llm_call_usage", "ai_model_id")).isTrue();

    long providerId = queryLong("""
        INSERT INTO ai_provider_instance (name, provider_type, enabled, config)
        VALUES ('OpenAI primary', 'openai', TRUE, ?::jsonb)
        RETURNING id
        """, "{\"apiKey\":\"test-key\",\"baseUrl\":\"https://api.openai.com/v1\"}");
    long modelId = queryLong("""
        INSERT INTO ai_model (provider_instance_id, display_name, model_id, enabled)
        VALUES (?, 'Test model', 'gpt-test', TRUE)
        RETURNING id
        """, providerId);
    execute("""
        INSERT INTO ai_llm_call_usage (
          call_id, purpose, source, call_kind, status, started_at
        )
        VALUES ('historic-null-provider-links', 'LEARNING_CHAT', 'LEARNING_CHAT', 'DIRECT', 'RUNNING', NOW())
        """);
    execute("""
        INSERT INTO ai_llm_call_usage (
          call_id, purpose, source, call_kind, provider_instance_id, ai_model_id, status, started_at
        )
        VALUES ('configured-provider-links', 'LEARNING_CHAT', 'LEARNING_CHAT', 'DIRECT', ?, ?, 'RUNNING', NOW())
        """, providerId, modelId);

    assertThat(queryLong("SELECT COUNT(*) FROM ai_llm_call_usage WHERE provider_instance_id IS NULL AND ai_model_id IS NULL"))
        .isEqualTo(1L);
    assertThat(queryLong("SELECT COUNT(*) FROM ai_llm_call_usage WHERE provider_instance_id = ? AND ai_model_id = ?", providerId, modelId))
        .isEqualTo(1L);
  }

  private boolean nullable(String tableName, String columnName) throws Exception {
    return "YES".equals(queryString("""
        SELECT is_nullable
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = ?
          AND column_name = ?
        """, tableName, columnName));
  }
}
