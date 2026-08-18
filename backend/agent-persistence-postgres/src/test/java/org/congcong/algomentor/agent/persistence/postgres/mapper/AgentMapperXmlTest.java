package org.congcong.algomentor.agent.persistence.postgres.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.congcong.algomentor.agent.persistence.postgres.json.AgentMessageRoleTypeHandler;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbMapTypeHandler;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.junit.jupiter.api.Test;

class AgentMapperXmlTest {

  @Test
  void mybatisLoadsAgentMapperXmlFiles() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.getTypeHandlerRegistry().register(new JsonbTypeHandler(new ObjectMapper()));
    configuration.getTypeHandlerRegistry().register(new JsonbMapTypeHandler(new ObjectMapper()));
    configuration.getTypeHandlerRegistry().register(new AgentMessageRoleTypeHandler());

    for (String resource : mapperResources()) {
      try (Reader reader = Resources.getResourceAsReader(resource)) {
        new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
      }
    }

    String conversationNamespace =
        "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentConversationMapper.";
    assertThat(configuration.hasStatement(conversationNamespace + "insertRun")).isTrue();
    assertThat(configuration.hasStatement(conversationNamespace + "attachTurnRun")).isTrue();
    assertThat(configuration.hasStatement(conversationNamespace + "findTurnMessagesByRunId")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunMapper.markRunFailed")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContextSnapshotMapper.insertSnapshot")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunTraceMapper.insertToolStart")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContentBlobMapper.insertBlob")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentAuditMapper.findRuns")).isTrue();
    boolean hasPrimitiveLongConstructorArg = configuration.getResultMap(
            "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentConversationMapper.AgentMessageMap")
        .getConstructorResultMappings()
        .stream()
        .map(mapping -> mapping.getJavaType())
        .anyMatch(long.class::equals);
    assertThat(hasPrimitiveLongConstructorArg).isTrue();
    boolean blobUsesPrimitiveByteArray = configuration.getResultMap(
            "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContentBlobMapper.ContentBlobMap")
        .getConstructorResultMappings()
        .stream()
        .map(mapping -> mapping.getJavaType())
        .anyMatch(byte[].class::equals);
    assertThat(blobUsesPrimitiveByteArray).isTrue();
  }

  @Test
  void findTurnMessagesSqlConstrainsRolesAndAssistantRun() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentConversationMapper.xml");

    assertThat(sql).contains(
        "JOIN agent_message u ON u.id = t.user_message_id AND u.status = 'active' AND u.role = 'user'");
    assertThat(sql).contains(
        "LEFT JOIN agent_message a ON a.id = t.assistant_message_id AND a.status = 'active' "
            + "AND a.role = 'assistant' AND a.run_id = r.id");
  }

  @Test
  void recentMessagesBeforeTurnSqlExcludesCurrentTurn() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentConversationMapper.xml");

    assertThat(sql).contains(
        "JOIN agent_turn message_turn ON message_turn.id = message.turn_id");
    assertThat(sql).contains(
        "message_turn.sequence_no &lt; ( SELECT current_turn.sequence_no FROM agent_turn current_turn "
            + "WHERE current_turn.id = #{turnId} AND current_turn.task_id = #{taskId} )");
  }

  @Test
  void newRunsPersistDiagnosticRetentionExpiry() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentConversationMapper.xml");

    assertThat(sql)
        .contains("diagnostic_retention_expires_at")
        .contains("NOW() + INTERVAL '30 days'");
  }

  @Test
  void startupRecoveryMarksOnlyRunningRunsAndTheirCurrentTurnsFailed() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentRunMapper.xml");

    assertThat(sql)
        .contains("<update id=\"failRunningRunsAtStartup\">")
        .contains("WHERE status = 'running'")
        .contains("'APPLICATION_RESTART'")
        .contains("<update id=\"failRunningTurnsAtStartup\">")
        .contains("AND current_run_id IN ( SELECT id FROM agent_run WHERE status = 'running' )");
  }

  @Test
  void successfulRunSqlClearsAnEarlierError() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentRunMapper.xml");

    assertThat(sql)
        .contains("<update id=\"markRunSucceeded\">")
        .contains("SET status = 'succeeded', error = NULL,");
  }

  @Test
  void runInsertSqlPersistsRuntimeAuditFields() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentConversationMapper.xml");

    assertThat(sql)
        .contains("agent_key")
        .contains("parent_step_index")
        .contains("#{triggerType}")
        .contains("#{retryOfRunId}")
        .contains("#{metadata,jdbcType=OTHER,typeHandler=org.congcong.algomentor.agent.persistence.postgres.json.JsonbMapTypeHandler}");
  }

  @Test
  void realtimeProtocolLookupReadsPersistedRunMetadata() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentConversationMapper.xml");

    assertThat(sql)
        .contains("<select id=\"findRealtimeProtocolVersion\" resultType=\"int\">")
        .contains("metadata -&gt;&gt; 'practiceRealtimeProtocolVersion' = '2'");
  }

  @Test
  void runLookupIncludesTerminalStatusAndErrorCodeForIdempotencyDecisions() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentConversationMapper.xml");

    assertThat(sql)
        .contains("r.status")
        .contains("r.error -&gt;&gt; 'code' AS error_code");
  }

  @Test
  void auditRunListSqlUsesStableStartTimeOrderingAndSupportsAllFilters() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentAuditMapper.xml");

    assertThat(sql)
        .contains("ORDER BY r.started_at")
        .contains("r.id <choose><when test=\"filter.direction.name() == 'ASC'\">ASC</when><otherwise>DESC</otherwise></choose>")
        .contains("filter.taskId != null")
        .contains("filter.turnId != null")
        .contains("filter.finishReason != null")
        .contains("filter.purpose != null")
        .contains("filter.source != null")
        .contains("filter.minCachedTokens != null")
        .contains("filter.maxCachedTokens != null")
        .contains("filter.minCacheRatio != null")
        .contains("filter.maxCacheRatio != null")
        .contains("filter.sort.name() == 'OVER_BUDGET'")
        .contains("filter.sort.name() == 'CACHE_RATIO'")
        .contains("filter.direction.name() == 'ASC'")
        .contains("findRunStatistics")
        .contains("COUNT(*) FILTER (WHERE metrics.compaction_applied)")
        .contains("LIMIT #{filter.pageSize} OFFSET #{filter.offset}");
  }

  @Test
  void auditToolResultSqlScopesContentToItsRunAndUsesBoundedReads() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentAuditMapper.xml");

    assertThat(sql)
        .contains("SUBSTRING(COALESCE(blob.content_text, '') FROM #{offset} + 1 FOR #{limit})")
        .contains("WHERE tc.run_id = #{runId} AND tc.tool_call_id = #{toolCallId}")
        .contains("r.diagnostic_redacted_at IS NULL")
        .contains("r.diagnostic_retention_expires_at IS NULL OR r.diagnostic_retention_expires_at &gt; NOW()");
  }

  @Test
  void auditSnapshotAndToolSqlGateDiagnosticContentByRunRetention() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentAuditMapper.xml");

    assertThat(sql)
        .contains("JOIN agent_run r ON r.id = s.run_id")
        .contains("r.diagnostic_retention_expires_at IS NULL OR r.diagnostic_retention_expires_at &gt; NOW()")
        .contains("THEN tc.arguments_json ELSE NULL END AS arguments")
        .contains("THEN tc.result_preview_json ELSE NULL END AS preview")
        .contains("THEN LEFT(u.content, 4000) ELSE NULL END)")
        .contains("AS user_message")
        .contains("THEN LEFT(a.content, 4000) ELSE NULL END)")
        .contains("AS assistant_message")
        .contains("includeRequestSnapshot");
  }

  @Test
  void auditStepTimelineProjectsMessageRolesWithoutLoadingMessageContent() throws Exception {
    String sql = normalizedResourceText("mapper/agent/AgentAuditMapper.xml");

    assertThat(sql)
        .contains("jsonb_array_elements(cs.messages_json) AS message(value)")
        .contains("jsonb_build_object('role', message.value ->> 'role')")
        .doesNotContain("cs.messages_json AS messages, NULL::JSONB AS tools");
  }

  private String normalizedResourceText(String resource) throws Exception {
    try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", " ");
    }
  }

  private List<String> mapperResources() {
    return List.of(
        "mapper/agent/AgentConversationMapper.xml",
        "mapper/agent/AgentRunMapper.xml",
        "mapper/agent/AgentRunTraceMapper.xml",
        "mapper/agent/AgentContentBlobMapper.xml",
        "mapper/agent/AgentContextSnapshotMapper.xml",
        "mapper/agent/AgentArtifactMapper.xml",
        "mapper/agent/AgentAuditMapper.xml");
  }
}
