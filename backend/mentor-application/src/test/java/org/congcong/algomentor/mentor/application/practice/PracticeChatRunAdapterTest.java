package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockConstants;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockRequest;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.LocalAgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.definition.AgentPreparedRequest;
import org.congcong.algomentor.agent.core.runtime.definition.AgentRunResource;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRun;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
import org.junit.jupiter.api.Test;

class PracticeChatRunAdapterTest {

  @Test
  void releasesRecallLeaseTogetherWithTheTaskLockWhenTheRunTerminates() {
    AtomicInteger recallReleases = new AtomicInteger();
    StubConversationService service = new StubConversationService(
        run("idem-1", false, recallReleases::incrementAndGet), Optional.empty());
    AgentRunLockManager lockManager = new InMemoryAgentRunLockManager();
    PracticeChatRunAdapter adapter = new PracticeChatRunAdapter(
        service, lockManager, new LocalAgentRunLockOwnerProvider("current-owner"));

    AgentPreparedRequest prepared = adapter.prepare(input("idem-1"));

    assertThat(service.prepareCalls).isEqualTo(1);
    assertThat(recallReleases).hasValue(0);
    assertThat(lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 11L, "other-owner", null, Map.of())).acquired()).isFalse();

    prepared.runResource().release();
    prepared.runResource().release();

    assertThat(recallReleases).hasValue(1);
    assertThat(lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 11L, "other-owner", null, Map.of())).acquired()).isTrue();
  }

  @Test
  void reusesTheExistingRunOnIdempotencyConflictWithoutPreparingAnotherRecallScope() {
    AtomicInteger unexpectedRecallRelease = new AtomicInteger();
    StubConversationService service = new StubConversationService(
        run("idem-1", false, unexpectedRecallRelease::incrementAndGet),
        Optional.of(run("idem-1", true, AgentRunResource.none())));
    AgentRunLockManager lockManager = new InMemoryAgentRunLockManager();
    lockManager.tryAcquire(new AgentRunLockRequest(
        AgentRunLockConstants.TASK_LOCK_KEY_PREFIX + 11L,
        "first-owner",
        null,
        Map.of(AgentRunLockConstants.IDEMPOTENCY_KEY_METADATA_KEY, "idem-1")));
    PracticeChatRunAdapter adapter = new PracticeChatRunAdapter(
        service, lockManager, new LocalAgentRunLockOwnerProvider("current-owner"));

    AgentPreparedRequest prepared = adapter.prepare(input("idem-1"));

    assertThat(prepared.idempotentReplay()).isTrue();
    assertThat(service.findCalls).isEqualTo(1);
    assertThat(service.prepareCalls).isZero();
    prepared.runResource().release();
    assertThat(unexpectedRecallRelease).hasValue(0);
  }

  private static PracticeChatAgentInput input(String idempotencyKey) {
    return new PracticeChatAgentInput(
        7L, 8L, 11L, 12L, 1, "two-sum", "给我一个提示", idempotencyKey,
        "zh-CN", PracticeCoachStyle.GUIDED, PracticeResponseLanguage.ZH_CN, 24);
  }

  private static AgentConversationRun run(String idempotencyKey, boolean replay, AgentRunResource resource) {
    Map<String, Object> metadata = replay
        ? Map.of(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true)
        : Map.of();
    AgentRequest request = new AgentRequest("run-13", idempotencyKey, List.of(LlmMessage.user("message")), metadata);
    PreparedAgentRun preparedRun = new PreparedAgentRun(
        11L, 12L, 13L, "run-13", idempotencyKey, "system", null, metadata);
    return new AgentConversationRun(11L, 12L, 13L, "run-13", request, preparedRun, resource);
  }

  private static final class StubConversationService extends AgentConversationService {

    private final AgentConversationRun preparedRun;
    private final Optional<AgentConversationRun> replayRun;
    private int prepareCalls;
    private int findCalls;

    private StubConversationService(AgentConversationRun preparedRun, Optional<AgentConversationRun> replayRun) {
      super(null, new ContextAssembler());
      this.preparedRun = preparedRun;
      this.replayRun = replayRun;
    }

    @Override
    public AgentConversationRun preparePracticeRun(PracticeChatAgentInput input) {
      prepareCalls++;
      return preparedRun;
    }

    @Override
    public Optional<AgentConversationRun> findPracticeRunByIdempotencyKey(PracticeChatAgentInput input) {
      findCalls++;
      return replayRun;
    }
  }
}
