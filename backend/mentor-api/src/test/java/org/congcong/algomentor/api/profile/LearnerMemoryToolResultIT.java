package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.toolresult.StoredToolResult;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContentBlobMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunTraceMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStartRow;
import org.congcong.algomentor.agent.persistence.postgres.repository.PostgresToolResultStore;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRecallToolContracts;
import org.junit.jupiter.api.Test;

class LearnerMemoryToolResultIT extends PostgresIntegrationTestSupport {

  @Test
  void readsMemoryResultOnlyFromOwningRunAndReturnsProvenance() throws Exception {
    migrateLatest();
    long userId = insertUser();
    RunFixture owningRun = insertRun(userId);
    RunFixture otherRun = insertRun(userId);
    var template = sqlSessionTemplate(
        "mapper/agent/AgentContentBlobMapper.xml",
        "mapper/agent/AgentRunTraceMapper.xml");
    AgentRunTraceMapper traceMapper = template.getMapper(AgentRunTraceMapper.class);
    traceMapper.insertToolStart(new ToolCallStartRow(
        owningRun.taskId(),
        owningRun.runId(),
        3,
        "memory-call",
        LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY,
        JsonNodeFactory.instance.objectNode(),
        "SUCCEEDED",
        0,
        0,
        "test",
        JsonNodeFactory.instance.objectNode(),
        Instant.now()));
    PostgresToolResultStore store = new PostgresToolResultStore(
        template.getMapper(AgentContentBlobMapper.class), traceMapper, new ObjectMapper());

    StoredToolResult stored = store.saveToolResult(
        context(owningRun, true),
        3,
        new LlmToolCall(
            "memory-call",
            LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY,
            JsonNodeFactory.instance.objectNode()),
        JsonNodeFactory.instance.objectNode(),
        "{\"items\":[\"bounded learner memory\"]}",
        "application/json",
        "test");

    assertThat(store.findByResultRef(context(owningRun, true), stored.resultRef()))
        .hasValueSatisfying(found -> {
          assertThat(found.contentText()).contains("bounded learner memory");
          assertThat(found.provenance().stepIndex()).isEqualTo(3);
          assertThat(found.provenance().toolCallId()).isEqualTo("memory-call");
          assertThat(found.provenance().toolName())
              .isEqualTo(LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY);
        });
    assertThat(store.findByResultRef(context(otherRun, true), stored.resultRef())).isEmpty();
    assertThat(store.findByResultRef(context(owningRun, false), stored.resultRef())).isEmpty();
  }

  private RunFixture insertRun(long userId) throws Exception {
    long taskId = queryLong(
        """
        INSERT INTO agent_task (user_id, status, context_policy, metadata, created_at, updated_at)
        VALUES (?, 'ACTIVE', '{}'::jsonb, '{}'::jsonb, NOW(), NOW())
        RETURNING id
        """,
        userId);
    long turnId = queryLong(
        """
        INSERT INTO agent_turn (task_id, sequence_no, status, created_at, updated_at)
        VALUES (?, 1, 'COMPLETED', NOW(), NOW())
        RETURNING id
        """,
        taskId);
    long runId = queryLong(
        """
        INSERT INTO agent_run (
          task_id, turn_id, run_uuid, attempt_no, idempotency_key, trigger_type, status, max_steps,
          usage, error, started_at, ended_at)
        VALUES (?, ?, ?, 1, ?, 'BACKGROUND', 'COMPLETED', 8, '{}'::jsonb, '{}'::jsonb, NOW(), NOW())
        RETURNING id
        """,
        taskId,
        turnId,
        UUID.randomUUID().toString(),
        "learner-memory-tool-result-" + UUID.randomUUID());
    return new RunFixture(taskId, runId);
  }

  private AgentLoopContext context(RunFixture run, boolean includeRunDbId) {
    Map<String, Object> metadata = includeRunDbId
        ? Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, run.runId())
        : Map.of();
    String runRef = "run-" + run.runId();
    AgentRequest request = new AgentRequest(runRef, runRef, List.of(LlmMessage.user("memory")), metadata);
    return new AgentLoopContext(runRef, request, 3, metadata);
  }

  private record RunFixture(long taskId, long runId) {
  }
}
