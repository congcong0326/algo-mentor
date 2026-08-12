package org.congcong.algomentor.agent.persistence.postgres.observer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.prompt.AgentPromptMetadataKeys;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContextSnapshotMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunTraceMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ContextSnapshotRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepEndUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepStartRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallEndUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStorageUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStartRow;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.provider.LlmProviderContinuation;
import org.congcong.algomentor.llm.core.provider.LlmProviderType;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmContentPart;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.junit.jupiter.api.Test;

class PersistentAgentTraceObserverTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final FakeSnapshotMapper mapper = new FakeSnapshotMapper();
  private final FakeRunTraceMapper runTraceMapper = new FakeRunTraceMapper();
  private final PersistentAgentTraceObserver observer = new PersistentAgentTraceObserver(
      mapper,
      runTraceMapper,
      new ObjectMapper(),
      Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void savesFinalLlmRequestSnapshotWithRuntimeMetadata() {
    Map<String, Object> metadata = Map.of(
        AgentRuntimeMetadataKeys.TASK_ID, 11L,
        AgentRuntimeMetadataKeys.RUN_DB_ID, 31L,
        AgentRuntimeMetadataKeys.TOKEN_BUDGET, 8_000,
        "apiToken", "secret");
    AgentRequest agentRequest = new AgentRequest(
        "agent-run-id",
        "request-id",
        List.of(LlmMessage.user("question")),
        metadata);
    AgentLoopContext context = new AgentLoopContext("agent-run-id", agentRequest, 4, metadata);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(new LlmModelSelector(
            LlmProviderId.of("openai"),
            LlmModelId.of("gpt-test"),
            Set.of(),
            "practice-chat"))
        .messages(List.of(new LlmMessage(
            LlmMessage.Role.USER,
            List.of(new LlmContentPart.Text("question")),
            null,
            null,
            Map.of(AgentPromptMetadataKeys.AUDIT_MESSAGE_SOURCE, "USER_INPUT"))))
        .build();

    observer.onLlmRequestReady(context, 1, request);

    ContextSnapshotRow row = mapper.row;
    assertThat(row.taskId()).isEqualTo(11L);
    assertThat(row.runId()).isEqualTo(31L);
    assertThat(row.stepIndex()).isEqualTo(1);
    assertThat(row.requestId()).isEqualTo("request-id");
    assertThat(row.provider()).isEqualTo("openai");
    assertThat(row.model()).isEqualTo("gpt-test");
    assertThat(row.modelSelector()).isEqualTo("practice-chat");
    assertThat(row.policyName()).isEqualTo("final-request-snapshot");
    assertThat(row.policyVersion()).isEqualTo("v1");
    assertThat(row.tokenBudget()).isEqualTo(8_000);
    assertThat(row.tokenEstimate()).isPositive();
    assertThat(row.snapshotStorageMode()).isEqualTo("inline");
    assertThat(row.requestSnapshotJson().get("modelSelector").get("providerId").asText()).isEqualTo("openai");
    assertThat(row.messagesJson().isArray()).isTrue();
    assertThat(row.messagesJson().get(0).get("auditSource").asText()).isEqualTo("USER_INPUT");
    assertThat(row.toolsJson().isArray()).isTrue();
    assertThat(row.generationOptions().isObject()).isTrue();
    assertThat(row.requestHash()).hasSize(64);
    assertThat(row.redactionPolicyVersion()).isEqualTo(PersistentAgentTraceObserver.REDACTION_POLICY_VERSION);
    assertThat(row.metadata().toString()).contains("[REDACTED]");
    assertThat(row.metadata().get("finalRequestTokenEstimate").asInt()).isPositive();
    assertThat(row.metadata().get("messageTokenEstimate").asInt()).isPositive();
    assertThat(row.metadata().get("toolsTokenEstimate").asInt()).isZero();
    assertThat(row.metadata().get("budgetStatus").asText()).isEqualTo("WITHIN_ESTIMATE");
    assertThat(row.createdAt()).isEqualTo(NOW);
    assertThat(runTraceMapper.attachedSnapshot).isEqualTo(new AttachedSnapshot(31L, 1, 51L));
  }

  @Test
  void excludesContinuationFromPersistedRequestSnapshot() {
    String sentinel = "trace-continuation-sentinel";
    LlmProviderContinuation continuation = new LlmProviderContinuation(
        LlmProviderType.of("compatible-test"),
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("secret", sentinel));
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "lookup",
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
    Map<String, Object> metadata = Map.of(
        AgentRuntimeMetadataKeys.TASK_ID, 11L,
        AgentRuntimeMetadataKeys.RUN_DB_ID, 31L);
    AgentRequest agentRequest = new AgentRequest("agent-run-id", "request-id", List.of(LlmMessage.user("question")), metadata);
    AgentLoopContext context = new AgentLoopContext("agent-run-id", agentRequest, 4, metadata);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(new LlmModelSelector(LlmProviderId.of("openai"), LlmModelId.of("gpt-test"), Set.of(), null))
        .messages(List.of(
            LlmMessage.assistantToolCalls(List.of(toolCall), continuation),
            LlmMessage.toolResult("call_1", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())))
        .build();

    observer.onLlmRequestReady(context, 2, request);

    assertThat(mapper.row.messagesJson().toString())
        .doesNotContain(sentinel, "providerContinuation", "payload");
    assertThat(mapper.row.requestSnapshotJson().toString())
        .doesNotContain(sentinel, "providerContinuation", "payload");
    assertThat(mapper.row.messagesJson())
        .extracting(message -> message.get("auditSource").asText())
        .containsExactly("MODEL_GENERATED", "TOOL_OUTPUT");
  }

  @Test
  void fallsBackToPromptAssemblyBudgetAndCapturesCompleteRequestEstimate() {
    Map<String, Object> metadata = Map.of(
        AgentRuntimeMetadataKeys.TASK_ID, 11L,
        AgentRuntimeMetadataKeys.RUN_DB_ID, 31L,
        AgentPromptMetadataKeys.PROMPT_TOKEN_BUDGET, 8_000,
        AgentPromptMetadataKeys.PROMPT_TOKEN_ESTIMATE, 3_500);
    AgentLoopContext context = new AgentLoopContext(
        "agent-run-id",
        new AgentRequest("agent-run-id", "request-id", List.of(LlmMessage.user("question")), metadata),
        4,
        metadata);
    LlmCompletionRequest request = LlmCompletionRequest.builder()
        .modelSelector(new LlmModelSelector(LlmProviderId.of("openai"), LlmModelId.of("gpt-test"), Set.of(), null))
        .messages(List.of(LlmMessage.user("question")))
        .tools(List.of(new org.congcong.algomentor.llm.core.tool.LlmToolSpec(
            "lookup", "look up data", com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(), false)))
        .build();

    observer.onLlmRequestReady(context, 1, request);

    assertThat(mapper.row.tokenBudget()).isEqualTo(8_000);
    assertThat(mapper.row.metadata().get("assemblyTokenEstimate").asInt()).isEqualTo(3_500);
    assertThat(mapper.row.metadata().get("toolsTokenEstimate").asInt()).isPositive();
    assertThat(mapper.row.metadata().get("finalRequestTokenEstimate").asInt())
        .isGreaterThan(mapper.row.metadata().get("messageTokenEstimate").asInt());
  }

  private static final class FakeSnapshotMapper implements AgentContextSnapshotMapper {
    private ContextSnapshotRow row;

    @Override
    public long insertSnapshot(ContextSnapshotRow row) {
      this.row = row;
      return 51L;
    }
  }

  private static final class FakeRunTraceMapper implements AgentRunTraceMapper {
    private AttachedSnapshot attachedSnapshot;

    @Override
    public int insertStepStart(RunStepStartRow row) {
      return 0;
    }

    @Override
    public int attachRequestSnapshot(long runId, int stepIndex, long requestSnapshotId) {
      attachedSnapshot = new AttachedSnapshot(runId, stepIndex, requestSnapshotId);
      return 1;
    }

    @Override
    public int markStepSucceeded(RunStepEndUpdate update) {
      return 0;
    }

    @Override
    public int markStepFailed(RunStepErrorUpdate update) {
      return 0;
    }

    @Override
    public int insertToolStart(ToolCallStartRow row) {
      return 0;
    }

    @Override
    public int markToolSucceeded(ToolCallEndUpdate update) {
      return 0;
    }

    @Override
    public int markToolFailed(ToolCallErrorUpdate update) {
      return 0;
    }

    @Override
    public Long findToolCallDbId(long runId, int stepIndex, String toolCallId) {
      return null;
    }

    @Override
    public int updateToolResultStorage(ToolCallStorageUpdate update) {
      return 0;
    }

    @Override
    public Long findRunIdByResultBlobId(long blobId) {
      return null;
    }

    @Override
    public org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolResultProvenanceRow
        findToolResultProvenanceByBlobId(long blobId) {
      return null;
    }
  }

  private record AttachedSnapshot(long runId, int stepIndex, long snapshotId) {
  }
}
