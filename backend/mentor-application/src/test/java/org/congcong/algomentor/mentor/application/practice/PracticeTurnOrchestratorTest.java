package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
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

  @Test
  void forwardsRunEndWithCapabilityMetadata() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    CapturingCoordinator coordinator = new CapturingCoordinator(runEnd(Map.of(
        AgentRuntimeMetadataKeys.RUN_DB_ID, 501L,
        "existing", "value")));
    InMemoryTurnMessageLookupRepository turnMessages = new InMemoryTurnMessageLookupRepository(messages());
    CapturingCapability capability = new CapturingCapability(new PracticeTurnCapabilityResult(
        PracticeCodeReviewConstants.METADATA_CODE_REVIEW,
        PracticeReviewStatus.SAVED,
        Map.of("reviewId", 900L, "versionNo", 2, "totalScore", "7.0", "passed", true)));
    PracticeTurnOrchestrator orchestrator = orchestrator(sessionRepository, coordinator, turnMessages, capability);

    List<AgentStreamEvent> events = collect(orchestrator.stream(
        7L,
        50L,
        completeCodeMessage(),
        "idem-1",
        "en-US",
        Map.of("requestId", "req-1")));

    assertThat(events).singleElement().isInstanceOfSatisfying(AgentStreamEvent.AgentRunEnd.class, event -> {
      assertThat(event.metadata()).containsEntry("existing", "value");
      assertThat(event.metadata()).containsKey(PracticeCodeReviewConstants.METADATA_PRACTICE_CAPABILITIES);
      assertThat(event.metadata().get(PracticeCodeReviewConstants.METADATA_PRACTICE_CAPABILITIES))
          .isInstanceOfSatisfying(Map.class, capabilities -> {
            assertThat(capabilities).containsKey(PracticeCodeReviewConstants.METADATA_CODE_REVIEW);
            assertThat(capabilities.get(PracticeCodeReviewConstants.METADATA_CODE_REVIEW))
                .isInstanceOfSatisfying(Map.class, review -> assertThat(review)
                    .containsEntry("status", PracticeReviewStatus.SAVED.name())
                    .containsEntry("reviewId", 900L)
                    .containsEntry("versionNo", 2)
                    .containsEntry("totalScore", "7.0")
                    .containsEntry("passed", true));
          });
    });
    assertThat(capability.calls).isEqualTo(1);
    assertThat(capability.context.userMessageId()).isEqualTo(701L);
    assertThat(capability.context.assistantMessageId()).isEqualTo(702L);
    assertThat(capability.context.agentRunDbId()).isEqualTo(501L);
    assertThat(capability.context.problemFacts())
        .contains("title=Two Sum")
        .contains("difficulty=EASY")
        .contains("tags=Array, Hash Table")
        .contains("Given an array of integers");
    assertThat(capability.context.learningPlanFacts())
        .contains("planId=12")
        .contains("phaseIndex=1")
        .contains("problemSlug=two-sum");
    assertThat(capability.classification.codeSubmissionCandidate()).isTrue();
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
  void preservesNonRunEndEventsBeforeRunEnd() {
    AgentStreamEvent.AgentRunStart start = new AgentStreamEvent.AgentRunStart("run-1", "topic", 1, Map.of("a", "b"));
    AgentStreamEvent.AgentStepEnd stepEnd = new AgentStreamEvent.AgentStepEnd("run-1", 1, LlmFinishReason.STOP, 0);
    CapturingCoordinator coordinator = new CapturingCoordinator(
        start,
        stepEnd,
        runEnd(Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, 501L)));
    PracticeTurnOrchestrator orchestrator = orchestrator(
        new InMemoryPracticeSessionRepository(),
        coordinator,
        new InMemoryTurnMessageLookupRepository(messages()),
        savedCapability());

    List<AgentStreamEvent> events = collect(orchestrator.stream(7L, 50L, completeCodeMessage(), "idem-1", null, null));

    assertThat(events).hasSize(3);
    assertThat(events.get(0)).isSameAs(start);
    assertThat(events.get(1)).isSameAs(stepEnd);
    assertThat(events.get(2)).isInstanceOf(AgentStreamEvent.AgentRunEnd.class);
  }

  @Test
  void touchesSessionAfterMergedRunEnd() {
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    PracticeTurnOrchestrator orchestrator = orchestrator(
        sessionRepository,
        new CapturingCoordinator(runEnd(Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, 501L))),
        new InMemoryTurnMessageLookupRepository(messages()),
        savedCapability());

    collect(new PracticeMessageStreamService(sessionRepository, orchestrator)
        .stream(7L, 50L, completeCodeMessage(), "idem-1", null, null));

    assertThat(sessionRepository.touchedSessionIds).containsExactly(50L);
  }

  @Test
  void missingTurnMessagesReturnsFailedCapabilityMetadata() {
    PracticeTurnOrchestrator orchestrator = orchestrator(
        new InMemoryPracticeSessionRepository(),
        new CapturingCoordinator(runEnd(Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, 501L))),
        new InMemoryTurnMessageLookupRepository(null),
        savedCapability());

    List<AgentStreamEvent> events = collect(orchestrator.stream(7L, 50L, completeCodeMessage(), "idem-1", null, null));

    AgentStreamEvent.AgentRunEnd end = (AgentStreamEvent.AgentRunEnd) events.get(0);
    assertThat(codeReviewMetadata(end))
        .containsEntry("status", PracticeReviewStatus.FAILED.name())
        .containsEntry("failureCode", "PRACTICE_TURN_MESSAGES_MISSING");
  }

  @Test
  void idempotentReplayReturnsExistingCapabilityMetadataWithoutRunningLlmReviewAgain() {
    ReplayAwareCapability capability = new ReplayAwareCapability(new PracticeTurnCapabilityResult(
        PracticeCodeReviewConstants.METADATA_CODE_REVIEW,
        PracticeReviewStatus.SAVED,
        Map.of("reviewId", 901L, "versionNo", 3, "totalScore", "8.0", "passed", true)));
    PracticeTurnOrchestrator orchestrator = orchestrator(
        new InMemoryPracticeSessionRepository(),
        new CapturingCoordinator(runEnd(Map.of(
            AgentRuntimeMetadataKeys.RUN_DB_ID, 501L,
            AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY, true))),
        new InMemoryTurnMessageLookupRepository(messages()),
        capability);

    List<AgentStreamEvent> events = collect(orchestrator.stream(7L, 50L, completeCodeMessage(), "idem-1", null, null));

    assertThat(capability.calls).isEqualTo(1);
    assertThat(capability.replayCalls).isEqualTo(1);
    assertThat(codeReviewMetadata((AgentStreamEvent.AgentRunEnd) events.get(0)))
        .containsEntry("status", PracticeReviewStatus.SAVED.name())
        .containsEntry("reviewId", 901L)
        .containsEntry("versionNo", 3)
        .containsEntry("totalScore", "8.0")
        .containsEntry("passed", true);
  }

  @Test
  void capabilityExceptionStillForwardsRunEnd() {
    CapturingCapability capability = new CapturingCapability(null);
    capability.failure = new IllegalStateException("secret implementation detail");
    PracticeTurnOrchestrator orchestrator = orchestrator(
        new InMemoryPracticeSessionRepository(),
        new CapturingCoordinator(runEnd(Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, 501L))),
        new InMemoryTurnMessageLookupRepository(messages()),
        capability);

    List<AgentStreamEvent> events = collect(orchestrator.stream(7L, 50L, completeCodeMessage(), "idem-1", null, null));

    assertThat(events).singleElement().isInstanceOf(AgentStreamEvent.AgentRunEnd.class);
    assertThat(codeReviewMetadata((AgentStreamEvent.AgentRunEnd) events.get(0)))
        .containsEntry("status", PracticeReviewStatus.FAILED.name())
        .containsEntry("failureCode", "PRACTICE_TURN_CAPABILITY_FAILED")
        .containsEntry("exceptionType", "IllegalStateException");
  }

  private PracticeTurnOrchestrator orchestrator(
      PracticeSessionRepository sessionRepository,
      AgentConversationRunCoordinator coordinator,
      AgentTurnMessageLookupRepository turnMessages,
      PracticeTurnCapability capability
  ) {
    return new PracticeTurnOrchestrator(
        sessionRepository,
        coordinator,
        turnMessages,
        new PracticeTurnClassifier(),
        new PracticeTurnCapabilityRegistry(List.of(capability)),
        (slug, locale) -> Optional.of(new PracticeChatProblemDetail(
            slug,
            1,
            "Two Sum",
            "EASY",
            List.of("Array", "Hash Table"),
            "Given an array of integers, return indices of the two numbers such that they add up to target.",
            "https://leetcode.com/problems/two-sum/")));
  }

  private CapturingCapability savedCapability() {
    return new CapturingCapability(new PracticeTurnCapabilityResult(
        PracticeCodeReviewConstants.METADATA_CODE_REVIEW,
        PracticeReviewStatus.SAVED,
        Map.of("reviewId", 900L, "versionNo", 2, "totalScore", "7.0", "passed", true)));
  }

  private Map<String, Object> codeReviewMetadata(AgentStreamEvent.AgentRunEnd event) {
    Object capabilities = event.metadata().get(PracticeCodeReviewConstants.METADATA_PRACTICE_CAPABILITIES);
    assertThat(capabilities).isInstanceOf(Map.class);
    Object review = ((Map<?, ?>) capabilities).get(PracticeCodeReviewConstants.METADATA_CODE_REVIEW);
    assertThat(review).isInstanceOf(Map.class);
    Map<String, Object> values = new LinkedHashMap<>();
    ((Map<?, ?>) review).forEach((key, value) -> values.put(String.valueOf(key), value));
    return values;
  }

  private AgentStreamEvent.AgentRunEnd runEnd(Map<String, Object> metadata) {
    return new AgentStreamEvent.AgentRunEnd("run-1", 1, LlmFinishReason.STOP, metadata);
  }

  private AgentTurnMessages messages() {
    return new AgentTurnMessages(
        501L,
        601L,
        message(701L, AgentMessage.Role.USER, completeCodeMessage()),
        message(702L, AgentMessage.Role.ASSISTANT, "可以，我来 review。"));
  }

  private AgentMessage message(long id, AgentMessage.Role role, String content) {
    return new AgentMessage(id, 100L, id, role, content, Instant.parse("2026-01-01T00:00:00Z"), Map.of());
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

  private static final class CapturingCapability implements PracticeTurnCapability {
    private final PracticeTurnCapabilityResult result;
    private RuntimeException failure;
    private int calls;
    private PracticeTurnContext context;
    private PracticeTurnClassification classification;

    private CapturingCapability(PracticeTurnCapabilityResult result) {
      this.result = result;
    }

    @Override
    public String capabilityName() {
      return PracticeCodeReviewConstants.METADATA_CODE_REVIEW;
    }

    @Override
    public PracticeTurnCapabilityResult afterTurn(PracticeTurnContext context, PracticeTurnClassification classification) {
      calls++;
      this.context = context;
      this.classification = classification;
      if (failure != null) {
        throw failure;
      }
      return result;
    }
  }

  private static final class ReplayAwareCapability implements PracticeTurnCapability {
    private final PracticeTurnCapabilityResult replayResult;
    private int calls;
    private int replayCalls;

    private ReplayAwareCapability(PracticeTurnCapabilityResult replayResult) {
      this.replayResult = replayResult;
    }

    @Override
    public String capabilityName() {
      return PracticeCodeReviewConstants.METADATA_CODE_REVIEW;
    }

    @Override
    public PracticeTurnCapabilityResult afterTurn(PracticeTurnContext context, PracticeTurnClassification classification) {
      calls++;
      if (classification.idempotentReplay()) {
        replayCalls++;
        return replayResult;
      }
      throw new AssertionError("Idempotent replay should be visible to the capability");
    }
  }

  private static final class InMemoryTurnMessageLookupRepository implements AgentTurnMessageLookupRepository {
    private final AgentTurnMessages messages;

    private InMemoryTurnMessageLookupRepository(AgentTurnMessages messages) {
      this.messages = messages;
    }

    @Override
    public Optional<AgentTurnMessages> findByRunId(long runId) {
      return Optional.ofNullable(messages).filter(value -> value.runId() == runId);
    }
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
