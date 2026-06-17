package org.congcong.algomentor.agent.persistence.postgres.observer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStepResult;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunTraceMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepEndUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStepStartRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallEndUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStorageUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.ToolCallStartRow;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.junit.jupiter.api.Test;

class PersistentAgentRunTraceObserverTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final FakeRunTraceMapper mapper = new FakeRunTraceMapper();
  private final PersistentAgentRunTraceObserver observer = new PersistentAgentRunTraceObserver(
      mapper,
      new ObjectMapper(),
      Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void writesStepAndToolTraceWithRedactedPayloads() {
    AgentLoopContext context = context();
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "fake_lookup",
        JsonNodeFactory.instance.objectNode()
            .put("query", "two pointers")
            .put("apiToken", "secret"));
    JsonNode result = JsonNodeFactory.instance.objectNode()
        .put("summary", "data")
        .put("password", "secret");

    observer.onRunStart(context);
    observer.onStepStart(context, 1);
    observer.onLlmEvent(context, 1, new LlmStreamEvent.MessageStart(
        LlmProviderId.of("openai"),
        LlmModelId.of("gpt-test")));
    observer.onLlmEvent(context, 1, new LlmStreamEvent.Usage(new LlmUsage(10, 5, 0, 0, 15)));
    observer.onToolStart(context, 1, toolCall);
    observer.onToolEnd(context, 1, toolCall, result);
    observer.onStepEnd(context, 1, new AgentStepResult(
        java.util.List.of(toolCall),
        LlmFinishReason.TOOL_CALLS));

    assertThat(mapper.stepStart).isEqualTo(new RunStepStartRow(
        11L,
        31L,
        1,
        "running",
        mapper.stepStart.metadata(),
        NOW));
    assertThat(mapper.stepStart.metadata().get("agentRunId").asText()).isEqualTo("agent-run-id");
    assertThat(mapper.toolStart.toolCallId()).isEqualTo("call_1");
    assertThat(mapper.toolStart.toolName()).isEqualTo("fake_lookup");
    assertThat(mapper.toolStart.argumentsJson().get("apiToken").asText()).isEqualTo("[REDACTED]");
    assertThat(mapper.toolStart.argumentCharCount()).isPositive();
    assertThat(mapper.toolStart.argumentTokenEstimate()).isPositive();
    assertThat(mapper.toolStart.redactionPolicyVersion()).isEqualTo(AgentTraceRedactor.POLICY_VERSION);
    assertThat(mapper.toolEnd.resultJson().get("password").asText()).isEqualTo("[REDACTED]");
    assertThat(mapper.toolEnd.durationMillis()).isZero();
    assertThat(mapper.toolEnd.resultCharCount()).isPositive();
    assertThat(mapper.toolEnd.resultLineCount()).isNull();
    assertThat(mapper.stepEnd.provider()).isEqualTo("openai");
    assertThat(mapper.stepEnd.model()).isEqualTo("gpt-test");
    assertThat(mapper.stepEnd.finishReason()).isEqualTo("TOOL_CALLS");
    assertThat(mapper.stepEnd.usage().get("totalTokens").asInt()).isEqualTo(15);
  }

  @Test
  void marksToolAndLatestStepFailed() {
    AgentLoopContext context = context();
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "fake_lookup",
        JsonNodeFactory.instance.objectNode());
    AgentException error = new AgentException(
        AgentErrorCode.TOOL_EXECUTION_FAILED,
        "tool failed",
        false,
        Map.of("apiToken", "secret"),
        null);

    observer.onRunStart(context);
    observer.onStepStart(context, 1);
    observer.onToolStart(context, 1, toolCall);
    observer.onToolError(context, 1, toolCall, error);
    observer.onError(context, error);

    assertThat(mapper.toolError.toolCallId()).isEqualTo("call_1");
    assertThat(mapper.toolError.status()).isEqualTo("failed");
    assertThat(mapper.toolError.error().get("metadata").get("apiToken").asText()).isEqualTo("[REDACTED]");
    assertThat(mapper.stepError.stepIndex()).isEqualTo(1);
    assertThat(mapper.stepError.status()).isEqualTo("failed");
    assertThat(mapper.stepError.error().get("code").asText()).isEqualTo("TOOL_EXECUTION_FAILED");
  }

  private AgentLoopContext context() {
    Map<String, Object> metadata = Map.of(
        AgentRuntimeMetadataKeys.TASK_ID, 11L,
        AgentRuntimeMetadataKeys.TURN_ID, 21L,
        AgentRuntimeMetadataKeys.RUN_DB_ID, 31L);
    AgentRequest request = new AgentRequest(
        "agent-run-id",
        "request-id",
        java.util.List.of(LlmMessage.user("question")),
        metadata);
    return new AgentLoopContext("agent-run-id", request, 4, metadata);
  }

  private static final class FakeRunTraceMapper implements AgentRunTraceMapper {
    private RunStepStartRow stepStart;
    private RunStepEndUpdate stepEnd;
    private RunStepErrorUpdate stepError;
    private ToolCallStartRow toolStart;
    private ToolCallEndUpdate toolEnd;
    private ToolCallErrorUpdate toolError;

    @Override
    public int insertStepStart(RunStepStartRow row) {
      stepStart = row;
      return 1;
    }

    @Override
    public int attachRequestSnapshot(long runId, int stepIndex, long requestSnapshotId) {
      return 1;
    }

    @Override
    public int markStepSucceeded(RunStepEndUpdate update) {
      stepEnd = update;
      return 1;
    }

    @Override
    public int markStepFailed(RunStepErrorUpdate update) {
      stepError = update;
      return 1;
    }

    @Override
    public int insertToolStart(ToolCallStartRow row) {
      toolStart = row;
      return 1;
    }

    @Override
    public int markToolSucceeded(ToolCallEndUpdate update) {
      toolEnd = update;
      return 1;
    }

    @Override
    public int markToolFailed(ToolCallErrorUpdate update) {
      toolError = update;
      return 1;
    }

    @Override
    public Long findToolCallDbId(long runId, int stepIndex, String toolCallId) {
      return 101L;
    }

    @Override
    public int updateToolResultStorage(ToolCallStorageUpdate update) {
      return 1;
    }

    @Override
    public Long findRunIdByResultBlobId(long blobId) {
      return 31L;
    }
  }
}
