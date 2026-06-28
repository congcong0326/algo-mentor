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
import org.congcong.algomentor.mentor.application.preference.UserAiPreference;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceRepository;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceService;
import org.junit.jupiter.api.Test;

class PracticeMessageStreamServiceTest {

  @Test
  void delegatesToOrchestratorAndTouchesOnMergedRunEnd() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    CapturingOrchestrator orchestrator = new CapturingOrchestrator(new AgentStreamEvent.AgentRunEnd(
        "run-1",
        1,
        LlmFinishReason.STOP,
        Map.of("existing", "metadata")));
    PracticeMessageStreamService service = new PracticeMessageStreamService(
        sessionRepository,
        orchestrator,
        new StubPreferenceService(PracticeCoachStyle.INTERVIEWER, PracticeResponseLanguage.EN_US));

    List<AgentStreamEvent> events = collect(service.stream(
        7,
        50,
        "给我一个提示",
        "idem-1",
        "en-US",
        Map.of("requestId", "req-1", PracticeChatPromptConstants.METADATA_PLAN_ID, 99L)));

    assertThat(events).singleElement().isInstanceOf(AgentStreamEvent.AgentRunEnd.class);
    assertThat(sessionRepository.touchedSessionIds).containsExactly(50L);
    assertThat(orchestrator.userId).isEqualTo(7L);
    assertThat(orchestrator.sessionId).isEqualTo(50L);
    assertThat(orchestrator.message).isEqualTo("给我一个提示");
    assertThat(orchestrator.idempotencyKey).isEqualTo("idem-1");
    assertThat(orchestrator.locale).isEqualTo("en-US");
    assertThat(orchestrator.governanceMetadata)
        .containsEntry("requestId", "req-1")
        .containsEntry(PracticeChatPromptConstants.METADATA_PLAN_ID, 99L)
        .containsEntry(PracticeChatPromptConstants.METADATA_COACH_STYLE, "INTERVIEWER")
        .containsEntry(PracticeChatPromptConstants.METADATA_RESPONSE_LANGUAGE, "EN_US");
  }

  @Test
  void forwardsInputsWithoutResolvingLocale() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    sessionRepository.session = sessionRepository.session(PracticeSessionStatus.ACTIVE, 100L, "en-US");
    CapturingOrchestrator orchestrator = new CapturingOrchestrator(new AgentStreamEvent.AgentRunEnd(
        "run-1",
        1,
        LlmFinishReason.STOP,
        Map.of()));
    PracticeMessageStreamService service = new PracticeMessageStreamService(sessionRepository, orchestrator);

    collect(service.stream(7, 50, "hint", "idem-1", "zh-CN", null));

    assertThat(orchestrator.locale).isEqualTo("zh-CN");
  }

  @Test
  void forwardsErrorWithoutTouchingSession() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    CapturingOrchestrator orchestrator = new CapturingOrchestrator(new IllegalStateException("stream failed"));
    PracticeMessageStreamService service = new PracticeMessageStreamService(sessionRepository, orchestrator);

    CollectingSubscriber subscriber = new CollectingSubscriber();
    service.stream(7, 50, "hint", "idem-1", null, null).subscribe(subscriber);

    assertThat(subscriber.error)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("stream failed");
    assertThat(sessionRepository.touchedSessionIds).isEmpty();
  }

  @Test
  void touchFailureDoesNotBreakRunEndOrCompletion() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    sessionRepository.touchFailure = new IllegalStateException("touch failed");
    CapturingOrchestrator orchestrator = new CapturingOrchestrator(new AgentStreamEvent.AgentRunEnd(
        "run-1",
        1,
        LlmFinishReason.STOP,
        Map.of()));
    PracticeMessageStreamService service = new PracticeMessageStreamService(sessionRepository, orchestrator);

    CollectingSubscriber subscriber = new CollectingSubscriber();
    service.stream(7, 50, "hint", "idem-1", null, null).subscribe(subscriber);

    assertThat(subscriber.events).singleElement().isInstanceOf(AgentStreamEvent.AgentRunEnd.class);
    assertThat(subscriber.completed).isTrue();
    assertThat(subscriber.error).isNull();
    assertThat(sessionRepository.touchedSessionIds).containsExactly(50L);
  }

  @Test
  void rejectsMissingSession() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    PracticeMessageStreamService service = new PracticeMessageStreamService(
        sessionRepository,
        new CapturingOrchestrator(new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, Map.of())));

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
        new CapturingOrchestrator(new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, Map.of())));

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
        new CapturingOrchestrator(new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, Map.of())));

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
    private RuntimeException touchFailure;

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
      if (touchFailure != null) {
        throw touchFailure;
      }
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

  private static final class CapturingOrchestrator extends PracticeTurnOrchestrator {

    private final Object result;
    private long userId;
    private long sessionId;
    private String message;
    private String idempotencyKey;
    private String locale;
    private Map<String, Object> governanceMetadata;

    private CapturingOrchestrator(Object result) {
      super(
          new InMemoryPracticeSessionRepository(),
          new UnusedCoordinator());
      this.result = result;
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(
        long userId,
        long sessionId,
        String message,
        String idempotencyKey,
        String locale,
        Map<String, Object> governanceMetadata
    ) {
      this.userId = userId;
      this.sessionId = sessionId;
      this.message = message;
      this.idempotencyKey = idempotencyKey;
      this.locale = locale;
      this.governanceMetadata = governanceMetadata == null ? Map.of() : Map.copyOf(governanceMetadata);
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

  private static final class StubPreferenceService extends UserAiPreferenceService {

    private final PracticeCoachStyle coachStyle;
    private final PracticeResponseLanguage responseLanguage;

    private StubPreferenceService(PracticeCoachStyle coachStyle, PracticeResponseLanguage responseLanguage) {
      super(UserAiPreferenceRepository.empty());
      this.coachStyle = coachStyle;
      this.responseLanguage = responseLanguage;
    }

    @Override
    public UserAiPreference get(long userId) {
      return new UserAiPreference(
          userId,
          coachStyle,
          responseLanguage,
          Instant.parse("2026-06-28T00:00:00Z"),
          Instant.parse("2026-06-28T00:00:00Z"));
    }
  }

  private static final class UnusedCoordinator extends AgentConversationRunCoordinator {
    private UnusedCoordinator() {
      super(
          new AgentConversationService(new UnusedConversationRepository(), new ContextAssembler()),
          new UnusedAgentLoopRunner(),
          new InMemoryAgentRunLockManager(),
          new LocalAgentRunLockOwnerProvider("owner-a"));
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
