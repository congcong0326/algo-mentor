package org.congcong.algomentor.mentor.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockConstants;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockRequest;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.LocalAgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.junit.jupiter.api.Test;

class MentorConversationAgentDefinitionTest {

  @Test
  void preparesTheExistingConversationContextAsOneStepNoToolRuntimeRunAndReleasesTaskLock() {
    CapturingRepository repository = new CapturingRepository();
    AgentRunLockManager lockManager = new InMemoryAgentRunLockManager();
    MentorConversationAgentDefinition definition = definition(repository, lockManager);
    MentorConversationAgentInput input = new MentorConversationAgentInput(42L, 7L, "请解释滑动窗口", "idem-1", 24);

    var prepared = definition.prepare(input, context(7L, "idem-1", 24));

    assertThat(definition.key().value()).isEqualTo(AiBusinessScenario.MENTOR_CONVERSATION.code());
    assertThat(definition.allowedToolNames()).isEmpty();
    assertThat(definition.loopPolicy().maxSteps()).isEqualTo(MentorConversationAgentDefinition.MAX_STEPS);
    assertThat(repository.lastRequest.taskId()).isEqualTo(42L);
    assertThat(repository.lastRequest.userId()).isEqualTo(7L);
    assertThat(repository.lastRequest.agentKey()).isEqualTo(MentorConversationAgentDefinition.KEY.value());
    assertThat(repository.lastRequest.maxSteps()).isEqualTo(MentorConversationAgentDefinition.MAX_STEPS);
    assertThat(prepared.preparedRun().taskId()).isEqualTo(42L);
    assertThat(prepared.messages()).extracting(LlmMessage::role)
        .containsExactly(
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.SYSTEM,
            LlmMessage.Role.USER,
            LlmMessage.Role.ASSISTANT,
            LlmMessage.Role.USER);
    assertThat(prepared.messages().get(1).text()).contains("active summary");
    assertThat(prepared.messages().get(4).text()).isEqualTo("请解释滑动窗口");
    assertThat(lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 42,
        "another-owner",
        null,
        Map.of())).acquired()).isFalse();

    prepared.runResource().release();

    assertThat(lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 42,
        "another-owner",
        null,
        Map.of())).acquired()).isTrue();
  }

  @Test
  void rejectsInvocationIdentityThatDoesNotMatchItsTrustedInput() {
    MentorConversationAgentDefinition definition = definition(new CapturingRepository(), new InMemoryAgentRunLockManager());
    MentorConversationAgentInput input = new MentorConversationAgentInput(null, 7L, "请解释滑动窗口", "idem-1", 24);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.prepare(input, context(8L, "idem-1", 24)))
        .withMessage("Mentor conversation input user does not match the invocation user");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> definition.prepare(input, context(7L, "other", 24)))
        .withMessage("Mentor conversation input idempotency key does not match the invocation");
  }

  private MentorConversationAgentDefinition definition(
      CapturingRepository repository,
      AgentRunLockManager lockManager
  ) {
    return new MentorConversationAgentDefinition(new MentorConversationRunAdapter(
        new AgentConversationService(repository, new org.congcong.algomentor.agent.core.runtime.context.ContextAssembler()),
        lockManager,
        new LocalAgentRunLockOwnerProvider("test-owner")));
  }

  private AgentInvocationContext context(long userId, String idempotencyKey, int requestSize) {
    return new AgentInvocationContext(
        userId,
        AgentInvocationMode.USER_ENTRY,
        idempotencyKey,
        null,
        null,
        requestSize,
        true);
  }

  private static final class CapturingRepository implements AgentConversationRepository {

    private AgentRunPreparationRequest lastRequest;

    @Override
    public PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request) {
      lastRequest = request;
      return new PreparedAgentRun(
          request.taskId() == null ? 42L : request.taskId(),
          2L,
          3L,
          "run-3",
          request.idempotencyKey(),
          request.systemPrompt(),
          "active summary",
          Map.of("repositoryMetadata", true),
          request.agentKey(),
          request.mode(),
          request.parentRunId(),
          request.parentStepIndex(),
          request.retryOfRunId(),
          request.maxSteps());
    }

    @Override
    public Optional<PreparedAgentRun> findRunByIdempotencyKey(String idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public List<AgentMessage> recentMessages(long taskId, int messageLimit) {
      return List.of(
          new AgentMessage(1L, taskId, 1L, AgentMessage.Role.USER, "旧问题", Instant.parse("2026-01-01T00:00:00Z")),
          new AgentMessage(2L, taskId, 2L, AgentMessage.Role.ASSISTANT, "旧回答", Instant.parse("2026-01-01T00:00:01Z")));
    }
  }
}
