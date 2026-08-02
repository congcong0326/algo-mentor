package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.preference.UserAiPreference;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceRepository;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceService;
import org.junit.jupiter.api.Test;

class PracticeMessageStreamServiceTest {

  @Test
  void validatesSessionBuildsTrustedInputAndTouchesOnlyAfterRunEnd() {
    InMemoryPracticeSessionRepository sessions = new InMemoryPracticeSessionRepository();
    CapturingOrchestrator orchestrator = new CapturingOrchestrator(runEnd());
    PracticeMessageStreamService service = new PracticeMessageStreamService(
        sessions, orchestrator, new StubPreferenceService(PracticeCoachStyle.DIRECT));

    collect(service.stream(7L, 50L, "给我一个提示", "idem-1", "en-US", 24));

    assertThat(orchestrator.input)
        .extracting(
            PracticeChatAgentInput::userId,
            PracticeChatAgentInput::practiceSessionId,
            PracticeChatAgentInput::agentTaskId,
            PracticeChatAgentInput::planId,
            PracticeChatAgentInput::phaseIndex,
            PracticeChatAgentInput::problemSlug,
            PracticeChatAgentInput::locale,
            PracticeChatAgentInput::coachStyle,
            PracticeChatAgentInput::responseLanguage,
            PracticeChatAgentInput::requestSize)
        .containsExactly(
            7L,
            50L,
            100L,
            12L,
            1,
            "two-sum",
            "en-US",
            PracticeCoachStyle.DIRECT,
            PracticeResponseLanguage.EN_US,
            24);
    assertThat(sessions.touchedSessionIds).containsExactly(50L);
  }

  @Test
  void doesNotTouchSessionWhenRuntimeStreamFails() {
    InMemoryPracticeSessionRepository sessions = new InMemoryPracticeSessionRepository();
    PracticeMessageStreamService service = new PracticeMessageStreamService(
        sessions, new CapturingOrchestrator(new IllegalStateException("stream failed")));

    CollectingSubscriber subscriber = new CollectingSubscriber();
    service.stream(7L, 50L, "hint", "idem-1", null, 4).subscribe(subscriber);

    assertThat(subscriber.error).isInstanceOf(IllegalStateException.class).hasMessage("stream failed");
    assertThat(sessions.touchedSessionIds).isEmpty();
  }

  @Test
  void rejectsMissingArchivedAndTasklessSessionsBeforeRuntime() {
    InMemoryPracticeSessionRepository sessions = new InMemoryPracticeSessionRepository();
    CapturingOrchestrator orchestrator = new CapturingOrchestrator(runEnd());
    PracticeMessageStreamService service = new PracticeMessageStreamService(sessions, orchestrator);

    assertThatThrownBy(() -> service.stream(7L, 51L, "hint", "idem-1", null, 4))
        .isInstanceOfSatisfying(LearningPlanException.class, exception ->
            assertThat(exception.code()).isEqualTo("PRACTICE_SESSION_NOT_FOUND"));
    sessions.session = sessions.session(PracticeSessionStatus.ARCHIVED, 100L, "zh-CN");
    assertThatThrownBy(() -> service.stream(7L, 50L, "hint", "idem-2", null, 4))
        .isInstanceOfSatisfying(LearningPlanException.class, exception ->
            assertThat(exception.code()).isEqualTo("PRACTICE_SESSION_ARCHIVED"));
    sessions.session = sessions.session(PracticeSessionStatus.ACTIVE, null, "zh-CN");
    assertThatThrownBy(() -> service.stream(7L, 50L, "hint", "idem-3", null, 4))
        .isInstanceOfSatisfying(LearningPlanException.class, exception ->
            assertThat(exception.code()).isEqualTo("PRACTICE_SESSION_AGENT_TASK_MISSING"));
    assertThat(orchestrator.calls).isZero();
  }

  private AgentStreamEvent.AgentRunEnd runEnd() {
    return new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, java.util.Map.of());
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
    private PracticeSession session = session(PracticeSessionStatus.ACTIVE, 100L, "zh-CN");
    private final List<Long> touchedSessionIds = new ArrayList<>();

    @Override
    public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession upsertAndLockSession(long userId, long planId, int phaseIndex, String problemSlug, String locale) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      return Optional.ofNullable(session).filter(value -> value.id() == sessionId && value.userId() == userId);
    }

    @Override
    public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void touchLastMessageAt(long sessionId) {
      touchedSessionIds.add(sessionId);
    }

    private PracticeSession session(PracticeSessionStatus status, Long taskId, String locale) {
      return new PracticeSession(50L, 7L, 12L, 1, "two-sum", status, taskId, 200L,
          PracticeProgressStatus.IN_PROGRESS, null, NOW, NOW, locale);
    }
  }

  private static final class CapturingOrchestrator extends PracticeTurnOrchestrator {
    private final Object result;
    private PracticeChatAgentInput input;
    private int calls;

    private CapturingOrchestrator(Object result) {
      super(new UnusedRuntime());
      this.result = result;
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(PracticeChatAgentInput input) {
      this.input = input;
      calls++;
      return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
        private boolean done;

        @Override
        public void request(long n) {
          if (done || n <= 0) {
            return;
          }
          done = true;
          if (result instanceof Throwable failure) {
            subscriber.onError(failure);
          } else {
            subscriber.onNext((AgentStreamEvent) result);
            subscriber.onComplete();
          }
        }

        @Override
        public void cancel() {
          done = true;
        }
      });
    }
  }

  private static final class StubPreferenceService extends UserAiPreferenceService {
    private final PracticeCoachStyle coachStyle;

    private StubPreferenceService(PracticeCoachStyle coachStyle) {
      super(UserAiPreferenceRepository.empty());
      this.coachStyle = coachStyle;
    }

    @Override
    public UserAiPreference get(long userId) {
      return new UserAiPreference(userId, coachStyle, Instant.EPOCH, Instant.EPOCH);
    }
  }

  private static final class UnusedRuntime implements AgentRuntime {
    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException();
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
}
