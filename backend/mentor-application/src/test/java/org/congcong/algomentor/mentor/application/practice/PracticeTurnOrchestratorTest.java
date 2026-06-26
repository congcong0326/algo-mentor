package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.LocalAgentRunLockOwnerProvider;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationCommand;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunCoordinator;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
import org.junit.jupiter.api.Test;

class PracticeTurnOrchestratorTest {

  private static final String LEGACY_PRACTICE_CAPABILITIES_METADATA = "practiceCapabilities";

  @Test
  void forwardsRunEventsWithoutPostRunReviewMetadata() {
    AgentStreamEvent.AgentRunStart start = new AgentStreamEvent.AgentRunStart("run-1", "topic", 1, Map.of("a", "b"));
    AgentStreamEvent.AgentStepEnd stepEnd = new AgentStreamEvent.AgentStepEnd("run-1", 1, LlmFinishReason.STOP, 0);
    AgentStreamEvent.AgentRunEnd runEnd = runEnd(Map.of(
        AgentRuntimeMetadataKeys.RUN_DB_ID, 501L,
        "existing", "value"));
    CapturingCoordinator coordinator = new CapturingCoordinator(start, stepEnd, runEnd);
    PracticeTurnOrchestrator orchestrator = orchestrator(new InMemoryPracticeSessionRepository(), coordinator);

    List<AgentStreamEvent> events = collect(orchestrator.stream(
        7L,
        50L,
        completeCodeMessage(),
        "idem-1",
        "en-US",
        Map.of("requestId", "req-1")));

    assertThat(events).containsExactly(start, stepEnd, runEnd);
    assertThat(((AgentStreamEvent.AgentRunEnd) events.get(2)).metadata())
        .containsEntry("existing", "value")
        .doesNotContainKey(LEGACY_PRACTICE_CAPABILITIES_METADATA);
    assertThat(coordinator.command.practiceChat())
        .isEqualTo(new PracticeChatReference(12L, 1, "two-sum", "zh-CN"));
    assertThat(coordinator.command.userId()).isEqualTo(7L);
    assertThat(coordinator.command.taskId()).isEqualTo(100L);
    assertThat(coordinator.command.governanceMetadata())
        .containsEntry("requestId", "req-1")
        .containsEntry(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, 50L)
        .containsEntry(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT)
        .containsEntry(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO)
        .containsEntry(PracticeChatPromptConstants.METADATA_PLAN_ID, 12L)
        .containsEntry(PracticeChatPromptConstants.METADATA_PHASE_INDEX, 1)
        .containsEntry(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, "two-sum")
        .containsEntry(PracticeChatPromptConstants.METADATA_LOCALE, "zh-CN");
  }

  @Test
  void touchesSessionAfterRunEndThroughStreamService() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    PracticeTurnOrchestrator orchestrator = orchestrator(
        sessionRepository,
        new CapturingCoordinator(runEnd(Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, 501L))));

    collect(new PracticeMessageStreamService(sessionRepository, orchestrator)
        .stream(7L, 50L, completeCodeMessage(), "idem-1", null, null));

    assertThat(sessionRepository.touchedSessionIds).containsExactly(50L);
  }

  private PracticeTurnOrchestrator orchestrator(
      PracticeSessionRepository sessionRepository,
      AgentConversationRunCoordinator coordinator
  ) {
    return new PracticeTurnOrchestrator(sessionRepository, coordinator);
  }

  private AgentStreamEvent.AgentRunEnd runEnd(Map<String, Object> metadata) {
    return new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, metadata);
  }

  private String completeCodeMessage() {
    return """
        ```java
        class Solution {
          public int[] twoSum(int[] nums, int target) {
            for (int i = 0; i < nums.length; i++) {
              for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target) {
                  return new int[] {i, j};
                }
              }
            }
            return new int[] {};
          }
        }
        ```
        """;
  }

  private List<AgentStreamEvent> collect(Flow.Publisher<AgentStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    assertThat(subscriber.error).isNull();
    assertThat(subscriber.completed).isTrue();
    return subscriber.events;
  }

  private static final class InMemoryPracticeSessionRepository implements PracticeSessionRepository {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final List<Long> touchedSessionIds = new ArrayList<>();
    private PracticeSession session = session(PracticeSessionStatus.ACTIVE, 100L, "zh-CN");

    @Override
    public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
      throw new UnsupportedOperationException("upsertAndAdvanceProgress not used");
    }

    @Override
    public PracticeSession upsertAndLockSession(
        long userId,
        long planId,
        int phaseIndex,
        String problemSlug,
        String locale
    ) {
      throw new UnsupportedOperationException("upsertAndLockSession not used");
    }

    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      return Optional.ofNullable(session)
          .filter(value -> value.id() == sessionId && value.userId() == userId);
    }

    @Override
    public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      throw new UnsupportedOperationException("attachAgentTask not used");
    }

    @Override
    public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      throw new UnsupportedOperationException("attachProblemStatementMessage not used");
    }

    @Override
    public PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status) {
      throw new UnsupportedOperationException("updateProgressStatus not used");
    }

    @Override
    public void touchLastMessageAt(long sessionId) {
      touchedSessionIds.add(sessionId);
    }

    private PracticeSession session(PracticeSessionStatus status, Long agentTaskId, String locale) {
      return new PracticeSession(
          50L,
          7L,
          12L,
          1,
          "two-sum",
          status,
          agentTaskId,
          200L,
          PracticeProgressStatus.IN_PROGRESS,
          null,
          NOW,
          NOW,
          locale);
    }
  }

  private static final class CapturingCoordinator extends AgentConversationRunCoordinator {
    private final List<AgentStreamEvent> events;
    private AgentConversationCommand command;

    private CapturingCoordinator(AgentStreamEvent... events) {
      super(
          new AgentConversationService(new UnusedConversationRepository(), new ContextAssembler()),
          new UnusedAgentLoopRunner(),
          new InMemoryAgentRunLockManager(),
          new LocalAgentRunLockOwnerProvider("owner-a"));
      this.events = List.of(events);
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentConversationCommand command) {
      this.command = command;
      return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
        private boolean completed;

        @Override
        public void request(long n) {
          if (completed || n <= 0) {
            return;
          }
          completed = true;
          events.forEach(subscriber::onNext);
          subscriber.onComplete();
        }

        @Override
        public void cancel() {
          completed = true;
        }
      });
    }
  }

  private static final class CollectingSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    private final List<AgentStreamEvent> events = new ArrayList<>();
    private Throwable error;
    private boolean completed;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent item) {
      events.add(item);
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
    }

    @Override
    public void onComplete() {
      completed = true;
    }
  }

  private static final class UnusedAgentLoopRunner extends AgentLoopRunner {
    private UnusedAgentLoopRunner() {
      super(new UnusedLlmGateway(), "stub-model", AgentToolRegistry.empty(), 1);
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
      throw new UnsupportedOperationException("agent loop runner not used");
    }
  }

  private static final class UnusedLlmGateway implements LlmGateway {
    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("complete not used");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("stream not used");
    }
  }

  private static final class UnusedConversationRepository implements AgentConversationRepository {
    @Override
    public PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request) {
      throw new UnsupportedOperationException("createOrReuseRun not used");
    }

    @Override
    public Optional<PreparedAgentRun> findRunByIdempotencyKey(String idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public List<AgentMessage> recentMessages(long taskId, int messageLimit) {
      return List.of();
    }
  }
}
