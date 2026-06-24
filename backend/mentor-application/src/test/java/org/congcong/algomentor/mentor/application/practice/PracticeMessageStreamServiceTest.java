package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.junit.jupiter.api.Test;

class PracticeMessageStreamServiceTest {

  @Test
  void streamsWithPracticeReferenceAndTouchesOnRunEnd() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    CapturingCoordinator coordinator = new CapturingCoordinator(new AgentStreamEvent.AgentRunEnd(
        "run-1",
        1,
        LlmFinishReason.STOP,
        Map.of()));
    PracticeMessageStreamService service = new PracticeMessageStreamService(sessionRepository, coordinator);

    List<AgentStreamEvent> events = collect(service.stream(
        7,
        50,
        "给我一个提示",
        "idem-1",
        "en-US",
        Map.of("requestId", "req-1", PracticeChatPromptConstants.METADATA_PLAN_ID, 99L)));

    assertThat(events).singleElement().isInstanceOf(AgentStreamEvent.AgentRunEnd.class);
    assertThat(sessionRepository.touchedSessionIds).containsExactly(50L);
    assertThat(coordinator.command.taskId()).isEqualTo(100L);
    assertThat(coordinator.command.practiceChat())
        .isEqualTo(new PracticeChatReference(12L, 1, "two-sum", "zh-CN"));
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
  void persistedNonDefaultLocaleWins() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    sessionRepository.session = sessionRepository.session(PracticeSessionStatus.ACTIVE, 100L, "en-US");
    CapturingCoordinator coordinator = new CapturingCoordinator(new AgentStreamEvent.AgentRunEnd(
        "run-1",
        1,
        LlmFinishReason.STOP,
        Map.of()));
    PracticeMessageStreamService service = new PracticeMessageStreamService(sessionRepository, coordinator);

    collect(service.stream(7, 50, "hint", "idem-1", "zh-CN", null));

    assertThat(coordinator.command.practiceChat())
        .isEqualTo(new PracticeChatReference(12L, 1, "two-sum", "en-US"));
    assertThat(coordinator.command.governanceMetadata())
        .containsEntry(PracticeChatPromptConstants.METADATA_LOCALE, "en-US");
  }

  @Test
  void forwardsErrorWithoutTouchingSession() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    CapturingCoordinator coordinator = new CapturingCoordinator(new IllegalStateException("stream failed"));
    PracticeMessageStreamService service = new PracticeMessageStreamService(sessionRepository, coordinator);

    CollectingSubscriber subscriber = new CollectingSubscriber();
    service.stream(7, 50, "hint", "idem-1", null, null).subscribe(subscriber);

    assertThat(subscriber.error)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("stream failed");
    assertThat(sessionRepository.touchedSessionIds).isEmpty();
  }

  @Test
  void rejectsMissingSession() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    PracticeMessageStreamService service = new PracticeMessageStreamService(
        sessionRepository,
        new CapturingCoordinator(new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, Map.of())));

    assertThatThrownBy(() -> service.stream(7, 51, "hint", "idem-1", null, null))
        .isInstanceOfSatisfying(LearningPlanException.class, exception ->
            assertThat(exception.code()).isEqualTo("PRACTICE_SESSION_NOT_FOUND"))
        .hasMessage("题目训练会话不存在。");
  }

  @Test
  void rejectsArchivedSession() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    sessionRepository.session = sessionRepository.session(PracticeSessionStatus.ARCHIVED, 100L, "zh-CN");
    PracticeMessageStreamService service = new PracticeMessageStreamService(
        sessionRepository,
        new CapturingCoordinator(new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, Map.of())));

    assertThatThrownBy(() -> service.stream(7, 50, "hint", "idem-1", null, null))
        .isInstanceOfSatisfying(LearningPlanException.class, exception ->
            assertThat(exception.code()).isEqualTo("PRACTICE_SESSION_ARCHIVED"))
        .hasMessage("题目训练会话已归档。");
  }

  @Test
  void rejectsMissingAgentTaskId() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    sessionRepository.session = sessionRepository.session(PracticeSessionStatus.ACTIVE, null, "zh-CN");
    PracticeMessageStreamService service = new PracticeMessageStreamService(
        sessionRepository,
        new CapturingCoordinator(new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, Map.of())));

    assertThatThrownBy(() -> service.stream(7, 50, "hint", "idem-1", null, null))
        .isInstanceOfSatisfying(LearningPlanException.class, exception ->
            assertThat(exception.code()).isEqualTo("PRACTICE_SESSION_AGENT_TASK_MISSING"))
        .hasMessage("题目训练会话缺少运行任务。");
  }

  private List<AgentStreamEvent> collect(Flow.Publisher<AgentStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    assertThat(subscriber.error).isNull();
    return subscriber.events;
  }

  private static final class InMemoryPracticeSessionRepository implements PracticeSessionRepository {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private PracticeSession session = session(PracticeSessionStatus.ACTIVE, 100L, "zh-CN");
    private final List<Long> touchedSessionIds = new ArrayList<>();

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
          50,
          7,
          12,
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

    private final Object result;
    private AgentConversationCommand command;

    private CapturingCoordinator(Object result) {
      super(
          new AgentConversationService(new UnusedConversationRepository(), new ContextAssembler()),
          new UnusedAgentLoopRunner(),
          new InMemoryAgentRunLockManager(),
          new LocalAgentRunLockOwnerProvider("owner-a"));
      this.result = result;
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
          if (result instanceof Throwable throwable) {
            subscriber.onError(throwable);
            return;
          }
          if (result instanceof AgentStreamEvent event) {
            subscriber.onNext(event);
          }
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
