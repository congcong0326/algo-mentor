package org.congcong.algomentor.mentor.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.junit.jupiter.api.Test;

class AgentConversationServiceTest {

  @Test
  void preparesRunWithMentorPromptAndRuntimeMetadata() {
    CapturingRepository repository = new CapturingRepository();
    repository.messages.add(new AgentMessage(
        1,
        11,
        1,
        AgentMessage.Role.USER,
        "什么是双指针？",
        Instant.parse("2026-01-01T00:00:00Z")));
    repository.messages.add(new AgentMessage(
        2,
        11,
        2,
        AgentMessage.Role.ASSISTANT,
        "双指针通常维护两个下标。",
        Instant.parse("2026-01-01T00:00:01Z")));
    AgentConversationService service = new AgentConversationService(repository, new ContextAssembler());

    AgentConversationRun run = service.prepareRun(new AgentConversationCommand(
        null,
        7L,
        "请讲滑动窗口",
        "idem-1"));

    assertThat(repository.lastRequest.userId()).isEqualTo(7L);
    assertThat(repository.lastRequest.userMessage()).isEqualTo("请讲滑动窗口");
    assertThat(repository.lastRequest.idempotencyKey()).isEqualTo("idem-1");
    assertThat(repository.lastRequest.systemPrompt()).contains("algorithm learning mentor");
    assertThat(repository.lastRequest.metadata()).containsEntry("triggerType", "user_request");

    assertThat(run.taskId()).isEqualTo(11);
    assertThat(run.turnId()).isEqualTo(21);
    assertThat(run.runId()).isEqualTo(31);
    assertThat(run.agentRequest().runId()).isEqualTo("run-uuid-31");
    assertThat(run.agentRequest().requestId()).isEqualTo("idem-1");
    assertThat(run.agentRequest().metadata())
        .containsEntry(AgentRuntimeMetadataKeys.TASK_ID, 11L)
        .containsEntry(AgentRuntimeMetadataKeys.TURN_ID, 21L)
        .containsEntry(AgentRuntimeMetadataKeys.RUN_DB_ID, 31L)
        .containsEntry(AgentRuntimeMetadataKeys.CONTEXT_POLICY, "sliding-window-with-active-summary")
        .containsEntry(AgentRuntimeMetadataKeys.TOKEN_BUDGET, 8_000)
        .containsEntry("title", "task-11");
    assertThat(run.agentRequest().messages())
        .extracting(LlmMessage::role)
        .containsExactly(
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.USER,
            LlmMessage.Role.ASSISTANT,
            LlmMessage.Role.USER);
  }

  @Test
  void findsExistingRunByIdempotencyKeyWithReplayMetadata() {
    CapturingRepository repository = new CapturingRepository();
    AgentConversationService service = new AgentConversationService(repository, new ContextAssembler());

    AgentConversationRun run = service.findRunByIdempotencyKey("idem-1", "请讲滑动窗口").orElseThrow();

    assertThat(run.idempotentReplay()).isTrue();
    assertThat(run.taskId()).isEqualTo(11);
    assertThat(run.agentRequest().metadata())
        .containsEntry(AgentRuntimeMetadataKeys.TASK_ID, 11L)
        .containsEntry(AgentRuntimeMetadataKeys.TURN_ID, 21L)
        .containsEntry(AgentRuntimeMetadataKeys.RUN_DB_ID, 31L)
        .containsEntry(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true);
  }

  private static final class CapturingRepository implements AgentConversationRepository {

    private final List<AgentMessage> messages = new ArrayList<>();
    private AgentRunPreparationRequest lastRequest;

    @Override
    public PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request) {
      this.lastRequest = request;
      return new PreparedAgentRun(
          11,
          21,
          31,
          "run-uuid-31",
          request.idempotencyKey(),
          request.systemPrompt(),
          null,
          Map.of("repositoryMetadata", true));
    }

    @Override
    public Optional<PreparedAgentRun> findRunByIdempotencyKey(String idempotencyKey) {
      return Optional.of(new PreparedAgentRun(
          11,
          21,
          31,
          "run-uuid-31",
          idempotencyKey,
          "system",
          null,
          Map.of(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true)));
    }

    @Override
    public List<AgentMessage> recentMessages(long taskId, int messageLimit) {
      assertThat(taskId).isEqualTo(11);
      assertThat(messageLimit).isEqualTo(16);
      return messages;
    }
  }
}
