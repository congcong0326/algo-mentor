package org.congcong.algomentor.mentor.application.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockConstants;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockRequest;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockToken;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.LocalAgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class AgentConversationRunCoordinatorTest {

  @Test
  void existingTaskConflictSkipsRunPreparation() {
    InMemoryAgentRunLockManager lockManager = new InMemoryAgentRunLockManager();
    AgentRunLockToken token = lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 42,
        "other-owner",
        null,
        Map.of("taskId", 42L))).token();
    CapturingConversationRepository repository = new CapturingConversationRepository();
    AgentConversationRunCoordinator coordinator = coordinator(repository, new CapturingAgentLoopRunner(), lockManager);

    assertThatThrownBy(() -> coordinator.stream(new AgentConversationCommand(
        42L,
        7L,
        "hello",
        "idem-1")))
        .isInstanceOf(AgentConversationRunInProgressException.class);
    assertThat(repository.lastRequest).isNull();

    lockManager.release(token);
  }

  @Test
  void streamAddsLockTokenToAgentRequest() {
    InMemoryAgentRunLockManager lockManager = new InMemoryAgentRunLockManager();
    CapturingConversationRepository repository = new CapturingConversationRepository();
    CapturingAgentLoopRunner runner = new CapturingAgentLoopRunner();
    AgentConversationRunCoordinator coordinator = coordinator(repository, runner, lockManager);

    coordinator.stream(new AgentConversationCommand(null, 7L, "hello", "idem-1"));

    assertThat(runner.lastRequest.metadata()).containsKey(AgentRunLockConstants.LOCK_TOKEN_METADATA_KEY);
    releaseCapturedLock(lockManager, runner.lastRequest);
  }

  @Test
  void subscribeFailureReleasesLock() {
    InMemoryAgentRunLockManager lockManager = new InMemoryAgentRunLockManager();
    CapturingConversationRepository repository = new CapturingConversationRepository();
    FailingSubscribeAgentLoopRunner runner = new FailingSubscribeAgentLoopRunner();
    AgentConversationRunCoordinator coordinator = coordinator(repository, runner, lockManager);
    Flow.Publisher<AgentStreamEvent> publisher = coordinator.stream(new AgentConversationCommand(
        42L,
        7L,
        "hello",
        "idem-1"));

    assertThatThrownBy(() -> publisher.subscribe(new NoopSubscriber()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("subscribe failed");
    assertThat(lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 42,
        "owner-a",
        null,
        Map.of("taskId", 42L))).acquired()).isTrue();
  }

  @Test
  void idempotentReplayReturnsExistingRunIdentityWithoutStartingAgentLoop() {
    InMemoryAgentRunLockManager lockManager = new InMemoryAgentRunLockManager();
    ReplayConversationRepository repository = new ReplayConversationRepository();
    CapturingAgentLoopRunner runner = new CapturingAgentLoopRunner();
    AgentConversationRunCoordinator coordinator = coordinator(repository, runner, lockManager);

    List<AgentStreamEvent> events = collect(coordinator.stream(new AgentConversationCommand(
        null,
        7L,
        "hello",
        "idem-1")));

    assertThat(runner.lastRequest).isNull();
    assertThat(events).extracting(AgentStreamEvent::name).containsExactly("agent_run_start", "agent_run_end");
    AgentStreamEvent.AgentRunStart start = (AgentStreamEvent.AgentRunStart) events.get(0);
    assertThat(start.runId()).isEqualTo("run-1");
    assertThat(start.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.TASK_ID, 42L)
        .containsEntry(AgentRuntimeMetadataKeys.TURN_ID, 2L)
        .containsEntry(AgentRuntimeMetadataKeys.RUN_DB_ID, 3L)
        .containsEntry(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true);
  }

  @Test
  void sameIdempotencyKeyCanReplayWhenTaskLockIsHeld() {
    InMemoryAgentRunLockManager lockManager = new InMemoryAgentRunLockManager();
    AgentRunLockToken token = lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 42,
        "owner-a",
        null,
        Map.of(
            "taskId", 42L,
            AgentRunLockConstants.IDEMPOTENCY_KEY_METADATA_KEY, "idem-1"))).token();
    ReplayConversationRepository repository = new ReplayConversationRepository();
    CapturingAgentLoopRunner runner = new CapturingAgentLoopRunner();
    AgentConversationRunCoordinator coordinator = coordinator(repository, runner, lockManager);

    List<AgentStreamEvent> events = collect(coordinator.stream(new AgentConversationCommand(
        42L,
        7L,
        "hello",
        "idem-1")));

    assertThat(runner.lastRequest).isNull();
    assertThat(repository.lastRequest).isNull();
    assertThat(events).extracting(AgentStreamEvent::name).containsExactly("agent_run_start", "agent_run_end");
    lockManager.release(token);
  }

  private AgentConversationRunCoordinator coordinator(
      CapturingConversationRepository repository,
      AgentLoopRunner runner,
      AgentRunLockManager lockManager
  ) {
    return new AgentConversationRunCoordinator(
        new AgentConversationService(repository, new ContextAssembler()),
        runner,
        lockManager,
        new LocalAgentRunLockOwnerProvider("owner-a"));
  }

  private void releaseCapturedLock(AgentRunLockManager lockManager, AgentRequest request) {
    Object token = request.metadata().get(AgentRunLockConstants.LOCK_TOKEN_METADATA_KEY);
    if (token instanceof AgentRunLockToken lockToken) {
      lockManager.release(lockToken);
    }
  }

  private List<AgentStreamEvent> collect(Flow.Publisher<AgentStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    return subscriber.events;
  }

  private static class CapturingConversationRepository implements AgentConversationRepository {
    protected AgentRunPreparationRequest lastRequest;

    @Override
    public PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request) {
      lastRequest = request;
      long taskId = request.taskId() == null ? 42L : request.taskId();
      return new PreparedAgentRun(
          taskId,
          2L,
          3L,
          "run-1",
          UUID.randomUUID().toString(),
          request.systemPrompt(),
          null,
          Map.of());
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

  private static final class ReplayConversationRepository extends CapturingConversationRepository {
    @Override
    public PreparedAgentRun createOrReuseRun(AgentRunPreparationRequest request) {
      lastRequest = request;
      long taskId = request.taskId() == null ? 42L : request.taskId();
      return new PreparedAgentRun(
          taskId,
          2L,
          3L,
          "run-1",
          request.idempotencyKey(),
          request.systemPrompt(),
          null,
          Map.of(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true));
    }

    @Override
    public Optional<PreparedAgentRun> findRunByIdempotencyKey(String idempotencyKey) {
      return Optional.of(new PreparedAgentRun(
          42L,
          2L,
          3L,
          "run-1",
          idempotencyKey,
          "system",
          null,
          Map.of(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true)));
    }
  }

  private static class CollectingSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    private final List<AgentStreamEvent> events = new java.util.ArrayList<>();

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
      throw new AssertionError(throwable);
    }

    @Override
    public void onComplete() {
    }
  }

  private static class CapturingAgentLoopRunner extends AgentLoopRunner {
    private AgentRequest lastRequest;

    CapturingAgentLoopRunner() {
      super(new UnusedLlmGateway(), "stub-model", AgentToolRegistry.empty(), 1);
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
      lastRequest = request;
      return subscriber -> {
        SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
        publisher.subscribe(subscriber);
        publisher.close();
      };
    }
  }

  private static class FailingSubscribeAgentLoopRunner extends AgentLoopRunner {
    FailingSubscribeAgentLoopRunner() {
      super(new UnusedLlmGateway(), "stub-model", AgentToolRegistry.empty(), 1);
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
      return subscriber -> {
        throw new IllegalStateException("subscribe failed");
      };
    }
  }

  private static class NoopSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent item) {
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onComplete() {
    }
  }

  private static class UnusedLlmGateway implements LlmGateway {

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("complete not used");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("stream not used");
    }
  }
}
