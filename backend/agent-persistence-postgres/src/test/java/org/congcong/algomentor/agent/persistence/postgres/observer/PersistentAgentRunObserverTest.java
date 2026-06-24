package org.congcong.algomentor.agent.persistence.postgres.observer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentOutput;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunErrorUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunStartUpdate;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.RunSuccessUpdate;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class PersistentAgentRunObserverTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final FakeRunMapper mapper = new FakeRunMapper();
  private final PersistentAgentRunObserver observer = new PersistentAgentRunObserver(
      mapper,
      objectMapper,
      Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void writesAssistantMessageAndMarksRunSucceeded() {
    AgentLoopContext context = context();
    mapper.nextAssistantMessageId = 41L;

    observer.onRunStart(context);
    observer.onLlmEvent(context, 1, new LlmStreamEvent.MessageStart(LlmProviderId.of("openai"), LlmModelId.of("gpt-test")));
    observer.onLlmEvent(context, 1, new LlmStreamEvent.ContentDelta("answer"));
    observer.onLlmEvent(context, 1, new LlmStreamEvent.Usage(new LlmUsage(10, 5, 0, 0, 15)));
    AgentOutput output = new AgentOutput("final answer", null, null, null, Map.of());
    observer.onFinalOutput(context, output);
    observer.onRunEnd(context, new AgentRunResult(1, LlmFinishReason.STOP, output, Map.of()));

    assertThat(mapper.startUpdate).isEqualTo(new RunStartUpdate(31L, 4, NOW));
    assertThat(mapper.insertedAssistantMessage)
        .isEqualTo(new InsertedAssistantMessage(11L, 21L, 31L, "final answer", 3, Map.of(), NOW, NOW));
    assertThat(mapper.successUpdate.runId()).isEqualTo(31L);
    assertThat(mapper.successUpdate.provider()).isEqualTo("openai");
    assertThat(mapper.successUpdate.model()).isEqualTo("gpt-test");
    assertThat(mapper.successUpdate.finishReason()).isEqualTo("STOP");
    assertThat(mapper.successUpdate.usage().get("inputTokens").asInt()).isEqualTo(10);
    assertThat(mapper.successUpdate.usage().get("outputTokens").asInt()).isEqualTo(5);
    assertThat(mapper.successUpdate.usage().get("cachedTokens").asInt()).isEqualTo(0);
    assertThat(mapper.successUpdate.usage().get("reasoningTokens").asInt()).isEqualTo(0);
    assertThat(mapper.successUpdate.usage().get("totalTokens").asInt()).isEqualTo(15);
    assertThat(mapper.turnSucceeded).isEqualTo(new TurnSucceeded(21L, 41L, 31L, NOW));
    assertThat(mapper.calls).containsExactly(
        "markRunStarted",
        "insertAssistantMessage",
        "markRunSucceeded",
        "markTurnSucceeded");
  }

  @Test
  void persistsOnlyFinalOutputWhenEarlierStepsStreamContent() {
    AgentLoopContext context = context();
    mapper.nextAssistantMessageId = 42L;
    AgentOutput output = new AgentOutput("final answer", null, null, null, Map.of());

    observer.onRunStart(context);
    observer.onLlmEvent(context, 1, new LlmStreamEvent.ContentDelta("intermediate tool explanation"));
    observer.onLlmEvent(context, 2, new LlmStreamEvent.ContentDelta("final answer"));
    observer.onFinalOutput(context, output);
    observer.onRunEnd(context, new AgentRunResult(2, LlmFinishReason.STOP, output, Map.of()));

    assertThat(mapper.insertedAssistantMessage.content()).isEqualTo("final answer");
    assertThat(mapper.turnSucceeded).isEqualTo(new TurnSucceeded(21L, 42L, 31L, NOW));
  }

  @Test
  void runEndDoesNotInsertAssistantMessageWithoutFinalOutput() {
    AgentLoopContext context = context();

    observer.onRunStart(context);
    observer.onLlmEvent(context, 1, new LlmStreamEvent.ContentDelta("token-only answer"));
    observer.onRunEnd(context, new AgentRunResult(1, LlmFinishReason.STOP, null, Map.of()));

    assertThat(mapper.insertedAssistantMessage).isNull();
    assertThat(mapper.turnSucceeded).isEqualTo(new TurnSucceeded(21L, null, 31L, NOW));
    assertThat(mapper.calls).containsExactly(
        "markRunStarted",
        "markRunSucceeded",
        "markTurnSucceeded");
  }

  @Test
  void marksRunFailedWithErrorPayload() {
    AgentLoopContext context = context();

    observer.onRunStart(context);
    observer.onError(context, new AgentException(
        AgentErrorCode.LLM_STREAM_FAILED,
        "stream failed",
        true,
        Map.of("provider", "openai"),
        null));

    assertThat(mapper.errorUpdate.runId()).isEqualTo(31L);
    assertThat(mapper.errorUpdate.status()).isEqualTo("failed");
    assertThat(mapper.errorUpdate.endedAt()).isEqualTo(NOW);
    assertThat(mapper.errorUpdate.error().get("code").asText()).isEqualTo("LLM_STREAM_FAILED");
    assertThat(mapper.errorUpdate.error().get("message").asText()).isEqualTo("stream failed");
    assertThat(mapper.errorUpdate.error().get("retryable").asBoolean()).isTrue();
    assertThat(mapper.errorUpdate.error().get("metadata").get("provider").asText()).isEqualTo("openai");
    assertThat(mapper.turnFailed).isEqualTo(new TurnFailed(21L, NOW));
    assertThat(mapper.calls).containsExactly(
        "markRunStarted",
        "markRunFailed",
        "markTurnFailed");
  }

  @Test
  void marksRunCancelledWhenAgentRunIsCancelled() {
    AgentLoopContext context = context();

    observer.onRunStart(context);
    observer.onError(context, new AgentException(
        AgentErrorCode.CANCELLED,
        "Agent run was cancelled",
        false,
        Map.of(),
        null));

    assertThat(mapper.errorUpdate.runId()).isEqualTo(31L);
    assertThat(mapper.errorUpdate.status()).isEqualTo("cancelled");
    assertThat(mapper.errorUpdate.error().get("code").asText()).isEqualTo("CANCELLED");
    assertThat(mapper.turnFailed).isEqualTo(new TurnFailed(21L, NOW));
  }

  @Test
  void writesAssistantMessageMetadataFromRequestMetadata() {
    FakeRunMapper mapper = new FakeRunMapper();
    PersistentAgentRunObserver observer = new PersistentAgentRunObserver(mapper, new ObjectMapper(), fixedClock());
    AgentRequest request = new AgentRequest(
        "run-1",
        "idem-1",
        List.of(LlmMessage.user("hello")),
        Map.of(
            AgentRuntimeMetadataKeys.TASK_ID, 10L,
            AgentRuntimeMetadataKeys.TURN_ID, 20L,
            AgentRuntimeMetadataKeys.RUN_DB_ID, 30L,
            "scenario", "PRACTICE_CHAT",
            "messageType", "CHAT",
            "practiceSessionId", 100L,
            "planId", 12L,
            "phaseIndex", 1,
            "problemSlug", "two-sum"));

    AgentLoopContext context = new AgentLoopContext("run-1", request, 4, request.metadata(), null);
    observer.onRunStart(context);
    observer.onFinalOutput(context, new AgentOutput("answer", null, null, null, Map.of()));

    assertThat(mapper.lastAssistantMetadata)
        .containsEntry("messageType", "CHAT")
        .containsEntry("scenario", "PRACTICE_CHAT")
        .containsEntry("practiceSessionId", 100L);
  }

  private AgentLoopContext context() {
    Map<String, Object> metadata = Map.of(
        AgentRuntimeMetadataKeys.TASK_ID, 11L,
        AgentRuntimeMetadataKeys.TURN_ID, 21L,
        AgentRuntimeMetadataKeys.RUN_DB_ID, 31L);
    AgentRequest request = new AgentRequest(
        "agent-run-id",
        "request-id",
        List.of(LlmMessage.user("question")),
        metadata);
    return new AgentLoopContext("agent-run-id", request, 4, metadata);
  }

  private static Clock fixedClock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }

  private static final class FakeRunMapper implements AgentRunMapper {
    private final List<String> calls = new ArrayList<>();
    private long nextAssistantMessageId;
    private Map<String, Object> lastAssistantMetadata = Map.of();
    private RunStartUpdate startUpdate;
    private InsertedAssistantMessage insertedAssistantMessage;
    private RunSuccessUpdate successUpdate;
    private TurnSucceeded turnSucceeded;
    private RunErrorUpdate errorUpdate;
    private TurnFailed turnFailed;

    @Override
    public int markRunStarted(RunStartUpdate update) {
      calls.add("markRunStarted");
      startUpdate = update;
      return 1;
    }

    @Override
    public long insertAssistantMessage(
        long taskId,
        long turnId,
        long runId,
        String content,
        int tokenEstimate,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
    ) {
      calls.add("insertAssistantMessage");
      lastAssistantMetadata = metadata;
      insertedAssistantMessage = new InsertedAssistantMessage(
          taskId,
          turnId,
          runId,
          content,
          tokenEstimate,
          metadata,
          createdAt,
          updatedAt);
      return nextAssistantMessageId;
    }

    @Override
    public int markRunSucceeded(RunSuccessUpdate update) {
      calls.add("markRunSucceeded");
      successUpdate = update;
      return 1;
    }

    @Override
    public int markTurnSucceeded(long turnId, Long assistantMessageId, long runId, Instant updatedAt) {
      calls.add("markTurnSucceeded");
      turnSucceeded = new TurnSucceeded(turnId, assistantMessageId, runId, updatedAt);
      return 1;
    }

    @Override
    public int markRunFailed(RunErrorUpdate update) {
      calls.add("markRunFailed");
      errorUpdate = update;
      return 1;
    }

    @Override
    public int markTurnFailed(long turnId, Instant updatedAt) {
      calls.add("markTurnFailed");
      turnFailed = new TurnFailed(turnId, updatedAt);
      return 1;
    }
  }

  private record InsertedAssistantMessage(
      long taskId,
      long turnId,
      long runId,
      String content,
      int tokenEstimate,
      Map<String, Object> metadata,
      Instant createdAt,
      Instant updatedAt
  ) {
  }

  private record TurnSucceeded(long turnId, Long assistantMessageId, long runId, Instant updatedAt) {
  }

  private record TurnFailed(long turnId, Instant updatedAt) {
  }
}
