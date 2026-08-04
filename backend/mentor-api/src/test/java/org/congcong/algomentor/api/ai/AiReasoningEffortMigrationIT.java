package org.congcong.algomentor.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.List;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class AiReasoningEffortMigrationIT extends PostgresIntegrationTestSupport {

  @Test
  void upgradesHistoricUsageAndAcceptsOnlyTheSevenReasoningEfforts() throws Exception {
    migrateTo("53");
    execute("""
        INSERT INTO ai_llm_call_usage (call_id, purpose, source, call_kind, status, started_at)
        VALUES ('historic-reasoning-effort', 'LEARNING_CHAT', 'LEARNING_CHAT', 'DIRECT', 'RUNNING', NOW())
        """);

    migrateLatest();

    assertThat(queryLong("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '54' AND success = TRUE"))
        .isEqualTo(1L);
    assertThat(nullable("ai_llm_call_usage", "reasoning_effort")).isTrue();
    assertThat(queryLong("""
        SELECT COUNT(*) FROM ai_llm_call_usage
        WHERE call_id = 'historic-reasoning-effort' AND reasoning_effort IS NULL
        """)).isEqualTo(1L);

    for (String effort : List.of("none", "minimal", "low", "medium", "high", "xhigh", "max")) {
      execute("""
          INSERT INTO ai_llm_call_usage (call_id, purpose, source, call_kind, status, reasoning_effort, started_at)
          VALUES (?, 'LEARNING_CHAT', 'LEARNING_CHAT', 'DIRECT', 'RUNNING', ?, NOW())
          """, "valid-reasoning-effort-" + effort, effort);
    }

    assertThatThrownBy(() -> execute("""
        INSERT INTO ai_llm_call_usage (call_id, purpose, source, call_kind, status, reasoning_effort, started_at)
        VALUES ('invalid-reasoning-effort', 'LEARNING_CHAT', 'LEARNING_CHAT', 'DIRECT', 'RUNNING', 'unsupported', NOW())
        """))
        .isInstanceOf(SQLException.class);
  }

  private boolean nullable(String tableName, String columnName) throws SQLException {
    return "YES".equals(queryString("""
        SELECT is_nullable
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = ?
          AND column_name = ?
        """, tableName, columnName));
  }
}
