package org.congcong.algomentor.api.admin.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Reader;
import java.time.Instant;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditQuery;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunFilter;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunSort;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditSortDirection;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentAuditMapper;
import org.congcong.algomentor.agent.persistence.postgres.repository.PostgresAgentAuditQuery;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

class AgentAuditMapperIT extends PostgresIntegrationTestSupport {

  @Test
  void readsOnlyTheRequestedTaskTurnRunStepAndToolTrace() throws Exception {
    migrateLatest();
    AuditFixture fixture = insertAuditFixture();
    insertUnrelatedRun();
    AgentAuditQuery query = auditQuery();

    var page = query.findRuns(new AgentAuditRunFilter(
        null, null, 1, 20, 7L, "PRACTICE_CHAT", "LEARNING", "practice", fixture.taskId(),
        fixture.turnId(), null, "openai", "gpt-test", "SUCCEEDED", "stop", true, true,
        true, false, 600L, 700L, 0.07D, 0.08D, AgentAuditRunSort.CACHE_RATIO, AgentAuditSortDirection.DESC));

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items()).singleElement().satisfies(run -> {
      assertThat(run.runId()).isEqualTo(fixture.runId());
      assertThat(run.taskId()).isEqualTo(fixture.taskId());
      assertThat(run.turnId()).isEqualTo(fixture.turnId());
      assertThat(run.userId()).isEqualTo(7L);
      assertThat(run.purpose()).isEqualTo("LEARNING");
      assertThat(run.source()).isEqualTo("practice");
      assertThat(run.stepCount()).isEqualTo(2);
      assertThat(run.toolCallCount()).isEqualTo(1);
      assertThat(run.finalRequestTokenEstimate()).isEqualTo(7_920);
      assertThat(run.actualInputTokens()).isEqualTo(8_762L);
      assertThat(run.cachedTokens()).isEqualTo(640L);
      assertThat(run.overBudgetTokens()).isEqualTo(762L);
      assertThat(run.compactionApplied()).isTrue();
      assertThat(run.compactionActionCount()).isEqualTo(2);
    });
    assertThat(page.statistics())
        .extracting(statistics -> statistics.runCount(), statistics -> statistics.overBudgetRunCount(),
            statistics -> statistics.compactionRunCount(), statistics -> statistics.usageReportedRunCount(),
            statistics -> statistics.inputTokens(), statistics -> statistics.cachedTokens(), statistics -> statistics.cacheRatio())
        .containsExactly(1L, 1L, 1L, 1L, 8_762L, 640L, 640D / 8_762D);

    var detail = query.findRun(fixture.runId()).orElseThrow();
    assertThat(detail.taskTurns()).singleElement().satisfies(turn -> {
      assertThat(turn.turnId()).isEqualTo(fixture.turnId());
      assertThat(turn.userMessage()).isEqualTo("Explain the failed test");
      assertThat(turn.assistantMessage()).isEqualTo("Use a boundary check");
      assertThat(turn.runAttempts()).singleElement().satisfies(attempt ->
          assertThat(attempt.runId()).isEqualTo(fixture.runId()));
    });
    assertThat(detail.steps()).extracting(step -> step.stepIndex()).containsExactly(1, 2);
    assertThat(detail.steps().get(0).toolCallCount()).isEqualTo(1);
    assertThat(detail.steps().get(1).usage().inputTokens()).isEqualTo(8_762L);

    var step = query.findStep(fixture.runId(), 1, true).orElseThrow();
    assertThat(step.messages()).isNotNull();
    assertThat(step.toolCalls()).singleElement().satisfies(tool -> {
      assertThat(tool.toolCallId()).isEqualTo("call_lookup");
      assertThat(tool.arguments().get("query").asText()).isEqualTo("edge case");
      assertThat(tool.preview().get("summary").asText()).isEqualTo("two matches");
    });

    var toolResult = query.findToolResult(fixture.runId(), "call_lookup", true, 0, 200).orElseThrow();
    assertThat(toolResult.content()).isEqualTo("complete tool output");
    assertThat(query.findToolResult(fixture.unrelatedRunId(), "call_lookup", true, 0, 200)).isEmpty();
  }

  @Test
  void hidesDiagnosticPayloadsAndTurnBodiesAfterRetentionExpires() throws Exception {
    migrateLatest();
    AuditFixture fixture = insertAuditFixture();
    execute("""
        UPDATE agent_run
        SET diagnostic_retention_expires_at = NOW() - INTERVAL '1 second'
        WHERE id = ?
        """, fixture.runId());
    AgentAuditQuery query = auditQuery();

    var detail = query.findRun(fixture.runId()).orElseThrow();
    assertThat(detail.taskTurns()).singleElement().satisfies(turn -> {
      assertThat(turn.userMessage()).isNull();
      assertThat(turn.assistantMessage()).isNull();
    });
    assertThat(detail.steps()).allSatisfy(step -> assertThat(step.snapshotAvailable()).isFalse());

    var step = query.findStep(fixture.runId(), 1, true).orElseThrow();
    assertThat(step.requestSnapshot()).isNull();
    assertThat(step.messages()).isNull();
    assertThat(step.tools()).isNull();
    assertThat(step.toolCalls()).singleElement().satisfies(tool -> {
      assertThat(tool.arguments()).isNull();
      assertThat(tool.result()).isNull();
      assertThat(tool.preview()).isNull();
    });

    var toolResult = query.findToolResult(fixture.runId(), "call_lookup", true, 0, 200).orElseThrow();
    assertThat(toolResult.preview()).isNull();
    assertThat(toolResult.content()).isNull();
    assertThat(toolResult.contentAvailable()).isFalse();
    assertThat(toolResult.retentionActive()).isFalse();
  }

  @Test
  void sortsMatchingRunsByCacheRatioAndAggregatesTheSameFilteredPopulation() throws Exception {
    migrateLatest();
    AuditFixture fixture = insertAuditFixture();
    long lowerCacheRunId = insertLowerCacheRun(fixture.taskId(), fixture.turnId());
    AgentAuditQuery query = auditQuery();

    var page = query.findRuns(new AgentAuditRunFilter(
        null, null, 1, 20, 7L, "PRACTICE_CHAT", "LEARNING", "practice", fixture.taskId(), fixture.turnId(),
        null, "openai", "gpt-test", "SUCCEEDED", "stop", null, null, null, false, null, null, null, null,
        AgentAuditRunSort.CACHE_RATIO, AgentAuditSortDirection.DESC));

    assertThat(page.items()).extracting(run -> run.runId()).containsExactly(fixture.runId(), lowerCacheRunId);
    assertThat(page.statistics())
        .extracting(statistics -> statistics.runCount(), statistics -> statistics.overBudgetRunCount(),
            statistics -> statistics.compactionRunCount(), statistics -> statistics.usageReportedRunCount(),
            statistics -> statistics.inputTokens(), statistics -> statistics.cachedTokens(), statistics -> statistics.cacheRatio())
        .containsExactly(2L, 1L, 1L, 2L, 11_762L, 740L, 740D / 11_762D);
  }

  private AgentAuditQuery auditQuery() throws Exception {
    Configuration configuration = new Configuration(new Environment(
        "agent-audit-it", new SpringManagedTransactionFactory(), dataSource()));
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.getTypeHandlerRegistry().register(new JsonbTypeHandler(new ObjectMapper()));
    try (Reader reader = Resources.getResourceAsReader("mapper/agent/AgentAuditMapper.xml")) {
      new XMLMapperBuilder(reader, configuration, "mapper/agent/AgentAuditMapper.xml", configuration.getSqlFragments())
          .parse();
    }
    SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
    SqlSessionTemplate sessionTemplate = new SqlSessionTemplate(factory);
    return new PostgresAgentAuditQuery(sessionTemplate.getMapper(AgentAuditMapper.class));
  }

  private AuditFixture insertAuditFixture() throws Exception {
    long taskId = queryLong("""
        INSERT INTO agent_task (user_id, title, status, context_policy, metadata, created_at, updated_at)
        VALUES (7, 'audit task', 'active', '{}'::jsonb, '{}'::jsonb, NOW(), NOW())
        RETURNING id
        """);
    long turnId = queryLong("""
        INSERT INTO agent_turn (task_id, sequence_no, status, created_at, updated_at)
        VALUES (?, 1, 'succeeded', NOW(), NOW())
        RETURNING id
        """, taskId);
    long runId = queryLong("""
        INSERT INTO agent_run (
          task_id, turn_id, run_uuid, attempt_no, idempotency_key, agent_key, trigger_type, status,
          provider, model, max_steps, finish_reason, usage, error, started_at, ended_at, diagnostic_retention_expires_at
        )
        VALUES (?, ?, 'audit-run', 1, 'audit-key', 'PRACTICE_CHAT', 'USER_ENTRY', 'succeeded',
          'openai', 'gpt-test', 4, 'stop', '{"inputTokens":8762,"cachedTokens":640}'::jsonb, '{}'::jsonb,
          NOW() - INTERVAL '10 seconds', NOW(), NOW() + INTERVAL '30 days')
        RETURNING id
        """, taskId, turnId);
    long userMessageId = queryLong("""
        INSERT INTO agent_message (task_id, turn_id, role, content, sequence_no, status, metadata, created_at, updated_at)
        VALUES (?, ?, 'user', 'Explain the failed test', 1, 'active', '{}'::jsonb, NOW(), NOW())
        RETURNING id
        """, taskId, turnId);
    long assistantMessageId = queryLong("""
        INSERT INTO agent_message (task_id, turn_id, run_id, role, content, sequence_no, status, metadata, created_at, updated_at)
        VALUES (?, ?, ?, 'assistant', 'Use a boundary check', 2, 'active', '{}'::jsonb, NOW(), NOW())
        RETURNING id
        """, taskId, turnId, runId);
    execute("UPDATE agent_turn SET user_message_id = ?, assistant_message_id = ? WHERE id = ?",
        userMessageId, assistantMessageId, turnId);

    long stepOneSnapshot = insertSnapshot(taskId, runId, 1, 4_000, false, "[]");
    long stepTwoSnapshot = insertSnapshot(taskId, runId, 2, 7_920, true,
        "[\"tool_result_preview\",\"section_drop\"]");
    execute("""
        INSERT INTO agent_run_step (
          task_id, run_id, step_index, request_snapshot_id, status, provider, model, finish_reason, usage,
          error, started_at, ended_at, metadata
        )
        VALUES (?, ?, 1, ?, 'succeeded', 'openai', 'gpt-test', 'tool_calls',
          '{"inputTokens":4000,"cachedTokens":0,"outputTokens":100,"totalTokens":4100}'::jsonb,
          '{}'::jsonb, NOW() - INTERVAL '9 seconds', NOW() - INTERVAL '8 seconds', '{}'::jsonb),
          (?, ?, 2, ?, 'succeeded', 'openai', 'gpt-test', 'stop',
          '{"inputTokens":8762,"cachedTokens":640,"outputTokens":400,"totalTokens":9162}'::jsonb,
          '{}'::jsonb, NOW() - INTERVAL '7 seconds', NOW() - INTERVAL '6 seconds', '{}'::jsonb)
        """, taskId, runId, stepOneSnapshot, taskId, runId, stepTwoSnapshot);
    long toolCallId = queryLong("""
        INSERT INTO agent_tool_call (
          task_id, run_id, step_index, tool_call_id, tool_name, arguments_json, result_json, status,
          duration_millis, argument_char_count, result_char_count, argument_token_estimate, result_token_estimate,
          error, redaction_policy_version, started_at, ended_at, metadata, result_storage_mode, result_preview_json,
          result_ref, result_line_count, result_sha256
        )
        VALUES (?, ?, 1, 'call_lookup', 'lookup', '{"query":"edge case"}'::jsonb,
          '{"summary":"two matches"}'::jsonb, 'succeeded', 12, 21, 20, 6, 5, '{}'::jsonb, 'v1',
          NOW() - INTERVAL '8 seconds', NOW() - INTERVAL '7 seconds', '{}'::jsonb, 'blob',
          '{"summary":"two matches"}'::jsonb, 'tool-result:1', 1, 'tool-hash')
        RETURNING id
        """, taskId, runId);
    long blobId = queryLong("""
        INSERT INTO agent_content_blob (
          scope_type, scope_id, content_type, storage_mode, content_text, sha256, char_count, byte_count,
          line_count, redaction_policy_version, metadata, created_at
        )
        VALUES ('tool_result', ?, 'text/plain', 'inline', 'complete tool output', 'tool-hash', 20, 20, 1, 'v1',
          '{}'::jsonb, NOW())
        RETURNING id
        """, toolCallId);
    execute("UPDATE agent_tool_call SET result_blob_id = ? WHERE id = ?", blobId, toolCallId);

    return new AuditFixture(taskId, turnId, runId, insertUnrelatedRun());
  }

  private long insertSnapshot(long taskId, long runId, int stepIndex, int finalEstimate, boolean compacted, String actions)
      throws Exception {
    String metadata = """
        '{"aiPurpose":"LEARNING","aiSource":"practice","promptTokenBudget":8000,
        "assemblyTokenEstimate":6800,"messageTokenEstimate":7600,"toolsTokenEstimate":200,
        "providerOverheadTokenEstimate":120,"finalRequestTokenEstimate":%d,
        "compactionApplied":%s,"compactionActions":%s}'::jsonb
        """.formatted(finalEstimate, compacted, actions);
    return queryLong("""
        INSERT INTO agent_context_snapshot (
          task_id, run_id, step_index, provider, model, policy_name, policy_version, token_budget, token_estimate,
          snapshot_storage_mode, request_snapshot_json, messages_json, tools_json, generation_options, request_hash,
          redaction_policy_version, retention_expires_at, metadata, created_at
        )
        VALUES (?, ?, ?, 'openai', 'gpt-test', 'final-request-snapshot', 'v1', 8000, ?, 'inline',
          '{"messages":[{"role":"user","content":"Explain the failed test"}]}'::jsonb,
          '[{"role":"user","content":"Explain the failed test","auditSource":"USER_INPUT"}]'::jsonb,
          '[{"name":"lookup","description":"Find examples","inputSchema":{"type":"object"}}]'::jsonb,
          '{}'::jsonb, 'request-hash', 'v1', NOW() + INTERVAL '30 days', %s, NOW())
        RETURNING id
        """.formatted(metadata), taskId, runId, stepIndex, finalEstimate);
  }

  private long insertUnrelatedRun() throws Exception {
    long taskId = queryLong("""
        INSERT INTO agent_task (user_id, title, status, context_policy, metadata, created_at, updated_at)
        VALUES (8, 'other task', 'active', '{}'::jsonb, '{}'::jsonb, NOW(), NOW())
        RETURNING id
        """);
    long turnId = queryLong("""
        INSERT INTO agent_turn (task_id, sequence_no, status, created_at, updated_at)
        VALUES (?, 1, 'succeeded', NOW(), NOW())
        RETURNING id
        """, taskId);
    String suffix = Long.toString(taskId);
    return queryLong("""
        INSERT INTO agent_run (
          task_id, turn_id, run_uuid, attempt_no, idempotency_key, agent_key, trigger_type, status,
          max_steps, usage, error, started_at, diagnostic_retention_expires_at
        )
        VALUES (?, ?, ?, 1, ?, 'OTHER', 'USER_ENTRY', 'succeeded', 1,
          '{}'::jsonb, '{}'::jsonb, NOW() - INTERVAL '20 seconds', NOW() + INTERVAL '30 days')
        RETURNING id
        """, taskId, turnId, "other-run-" + suffix, "other-key-" + suffix);
  }

  private long insertLowerCacheRun(long taskId, long turnId) throws Exception {
    long runId = queryLong("""
        INSERT INTO agent_run (
          task_id, turn_id, run_uuid, attempt_no, idempotency_key, agent_key, trigger_type, status,
          provider, model, max_steps, finish_reason, usage, error, started_at, ended_at, diagnostic_retention_expires_at
        )
        VALUES (?, ?, 'audit-low-cache-run', 2, 'audit-low-cache-key', 'PRACTICE_CHAT', 'USER_ENTRY', 'succeeded',
          'openai', 'gpt-test', 4, 'stop', '{"inputTokens":3000,"cachedTokens":100}'::jsonb, '{}'::jsonb,
          NOW() - INTERVAL '5 seconds', NOW(), NOW() + INTERVAL '30 days')
        RETURNING id
        """, taskId, turnId);
    long snapshotId = insertSnapshot(taskId, runId, 1, 3_000, false, "[]");
    execute("""
        INSERT INTO agent_run_step (
          task_id, run_id, step_index, request_snapshot_id, status, provider, model, finish_reason, usage,
          error, started_at, ended_at, metadata
        )
        VALUES (?, ?, 1, ?, 'succeeded', 'openai', 'gpt-test', 'stop',
          '{"inputTokens":3000,"cachedTokens":100,"outputTokens":100,"totalTokens":3100}'::jsonb,
          '{}'::jsonb, NOW() - INTERVAL '4 seconds', NOW() - INTERVAL '3 seconds', '{}'::jsonb)
        """, taskId, runId, snapshotId);
    return runId;
  }

  private record AuditFixture(long taskId, long turnId, long runId, long unrelatedRunId) {
  }
}
