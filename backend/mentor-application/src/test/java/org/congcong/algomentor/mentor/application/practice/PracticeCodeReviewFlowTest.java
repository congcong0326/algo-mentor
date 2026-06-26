package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionGuard;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHookChain;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionResultFactory;
import org.congcong.algomentor.agent.core.permission.InMemoryAgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultJsonKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultTypes;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.LocalAgentRunLockOwnerProvider;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmContentPart;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationCommand;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunCoordinator;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewFlowTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String LEGACY_PRACTICE_CAPABILITIES_METADATA = "practiceCapabilities";
  private static final long USER_ID = 7L;
  private static final long SESSION_ID = 50L;
  private static final long PLAN_ID = 12L;
  private static final int PHASE_INDEX = 1;
  private static final long RUN_DB_ID = 501L;
  private static final long TASK_ID = 100L;
  private static final long TURN_ID = 200L;
  private static final long USER_MESSAGE_ID = 701L;
  private static final long ASSISTANT_MESSAGE_ID = 702L;
  private static final String PROBLEM_SLUG = "merge-sorted-array";

  @Test
  void plainPastedMergeSortedArrayCodeDoesNotAutoSaveReview() {
    InMemoryReviewRepository reviewRepository = new InMemoryReviewRepository();
    PracticeMessageStreamService streamService = streamService();

    List<AgentStreamEvent> events = collect(streamService.stream(
        USER_ID,
        SESSION_ID,
        plainPastedMergeCode(),
        "idem-plain-code",
        "zh-CN",
        Map.of("requestId", "req-1")));

    AgentStreamEvent.AgentRunEnd runEnd = onlyRunEnd(events);
    assertThat(runEnd.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.RUN_DB_ID, 501L)
        .doesNotContainKey(LEGACY_PRACTICE_CAPABILITIES_METADATA);
    assertThat(reviewRepository.savedDrafts).isEmpty();

    PracticeCompletionGate gate = new PracticeCompletionGateService(reviewRepository)
        .evaluate(USER_ID, session());
    assertThat(gate.canComplete()).isFalse();
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.NO_REVIEW);
  }

  @Test
  void reviewToolAskAllowPersistsOnlyAfterUserAllows() {
    InMemoryReviewRepository reviewRepository = new InMemoryReviewRepository();
    InMemoryPracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    InMemoryTurnMessageLookupRepository turnMessageLookupRepository =
        new InMemoryTurnMessageLookupRepository(turnMessages());
    SavingReviewService reviewService = new SavingReviewService(reviewRepository);
    FakeGateway gateway = reviewToolGateway("Review saved.");
    InMemoryAgentToolPermissionCoordinator coordinator = permissionCoordinator(Duration.ofSeconds(5));
    AgentLoopRunner runner = practiceRunner(
        gateway,
        sessionRepository,
        turnMessageLookupRepository,
        reviewService,
        coordinator);
    PermissionDecisionSubscriber subscriber = new PermissionDecisionSubscriber();

    runner.stream(reviewAgentRequest("run-review-allow")).subscribe(subscriber);

    AgentStreamEvent.ToolPermissionRequest request = subscriber.awaitPermissionRequest();
    assertThat(request.toolName()).isEqualTo(PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW);
    assertThat(reviewRepository.savedDrafts).isEmpty();
    assertThat(reviewRepository.findLatest(USER_ID, SESSION_ID)).isEmpty();

    coordinator.decide(
        request.permissionRequestId(),
        AgentToolPermissionDecisionType.ALLOW,
        "user_confirmed",
        USER_ID);
    List<AgentStreamEvent> events = subscriber.awaitCompletion();

    assertThat(coordinator.pendingRequestCount()).isZero();
    assertThat(reviewRepository.savedDrafts).hasSize(1);
    assertThat(reviewRepository.findLatest(USER_ID, SESSION_ID)).isPresent();
    assertThat(events).extracting(AgentStreamEvent::name).containsSubsequence(
        "tool_permission_request",
        "tool_permission_decision",
        "agent_tool_start",
        "agent_tool_end",
        "agent_run_end");
    LlmContentPart.ToolResult toolResult = toolResultFromSecondLlmRequest(gateway);
    assertThat(toolResult.result().path(PracticeCodeReviewAgentToolNames.RESULT_TYPE).asText())
        .isEqualTo(PracticeCodeReviewAgentToolNames.RESULT_TYPE_PRACTICE_CODE_REVIEW_SUBMITTED);
    assertThat(toolResult.result().path(PracticeCodeReviewAgentToolNames.RESULT_STATUS).asText())
        .isEqualTo(PracticeReviewStatus.SAVED.name());
    assertThat(toolResult.result().path(PracticeCodeReviewAgentToolNames.RESULT_REVIEW_ID).asLong()).isEqualTo(900L);
  }

  @Test
  void reviewToolAskDenyDoesNotPersistAndRunContinuesWithSyntheticResult() {
    InMemoryReviewRepository reviewRepository = new InMemoryReviewRepository();
    FakeGateway gateway = reviewToolGateway("Review denied.");
    InMemoryAgentToolPermissionCoordinator coordinator = permissionCoordinator(Duration.ofSeconds(5));
    AgentLoopRunner runner = practiceRunner(
        gateway,
        new InMemoryPracticeSessionRepository(),
        new InMemoryTurnMessageLookupRepository(turnMessages()),
        new SavingReviewService(reviewRepository),
        coordinator);
    PermissionDecisionSubscriber subscriber = new PermissionDecisionSubscriber();

    runner.stream(reviewAgentRequest("run-review-deny")).subscribe(subscriber);

    AgentStreamEvent.ToolPermissionRequest request = subscriber.awaitPermissionRequest();
    assertThat(reviewRepository.savedDrafts).isEmpty();
    coordinator.decide(
        request.permissionRequestId(),
        AgentToolPermissionDecisionType.DENY,
        "user_rejected",
        USER_ID);
    List<AgentStreamEvent> events = subscriber.awaitCompletion();

    assertThat(coordinator.pendingRequestCount()).isZero();
    assertThat(reviewRepository.savedDrafts).isEmpty();
    assertThat(reviewRepository.findLatest(USER_ID, SESSION_ID)).isEmpty();
    assertThat(events).extracting(AgentStreamEvent::name)
        .contains("tool_permission_request", "tool_permission_decision", "agent_tool_end", "agent_run_end")
        .doesNotContain("agent_tool_start");
    AgentStreamEvent.AgentToolEnd toolEnd = onlyToolEnd(events);
    assertThat(toolEnd.result().path(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_DENIED);
    assertThat(toolEnd.result().path(AgentToolResultJsonKeys.REASON).asText()).isEqualTo("user_rejected");
    assertThat(toolResultFromSecondLlmRequest(gateway).result()).isEqualTo(toolEnd.result());
  }

  @Test
  void reviewToolAskTimeoutDoesNotPersistAndRunContinuesWithSyntheticResult() {
    InMemoryReviewRepository reviewRepository = new InMemoryReviewRepository();
    FakeGateway gateway = reviewToolGateway("Review timed out.");
    InMemoryAgentToolPermissionCoordinator coordinator = permissionCoordinator(Duration.ofMillis(1));
    AgentLoopRunner runner = practiceRunner(
        gateway,
        new InMemoryPracticeSessionRepository(),
        new InMemoryTurnMessageLookupRepository(turnMessages()),
        new SavingReviewService(reviewRepository),
        coordinator);

    List<AgentStreamEvent> events = collect(runner.stream(reviewAgentRequest("run-review-timeout")));

    assertThat(coordinator.pendingRequestCount()).isZero();
    assertThat(reviewRepository.savedDrafts).isEmpty();
    assertThat(reviewRepository.findLatest(USER_ID, SESSION_ID)).isEmpty();
    assertThat(events).extracting(AgentStreamEvent::name)
        .contains("tool_permission_request", "tool_permission_timeout", "agent_tool_end", "agent_run_end")
        .doesNotContain("tool_permission_decision", "agent_tool_start");
    AgentStreamEvent.AgentToolEnd toolEnd = onlyToolEnd(events);
    assertThat(toolEnd.result().path(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_TIMEOUT);
    assertThat(toolEnd.result().path(AgentToolResultJsonKeys.RETRYABLE).asBoolean()).isTrue();
    assertThat(toolResultFromSecondLlmRequest(gateway).result()).isEqualTo(toolEnd.result());
  }

  private PracticeMessageStreamService streamService() {
    PracticeSessionRepository sessionRepository = new InMemoryPracticeSessionRepository();
    PracticeTurnOrchestrator orchestrator = new PracticeTurnOrchestrator(
        sessionRepository,
        new CapturingCoordinator(runEnd()));
    return new PracticeMessageStreamService(sessionRepository, orchestrator);
  }

  private String plainPastedMergeCode() {
    return """
        class Solution {
            public void merge(int[] nums1, int m, int[] nums2, int n) {
                int[] temp = new int[m + n];

                int i = 0;
                int j = 0;
                int k = 0;

                while (i < m && j < n) {
                    if (nums1[i] <= nums2[j]) {
                        temp[k] = nums1[i];
                        i++;
                    } else {
                        temp[k] = nums2[j];
                        j++;
                    }
                    k++;
                }

                while (i < m) {
                    temp[k] = nums1[i];
                    i++;
                    k++;
                }

                while (j < n) {
                    temp[k] = nums2[j];
                    j++;
                    k++;
                }

                for (int x = 0; x < m + n; x++) {
                    nums1[x] = temp[x];
                }
            }
        }
        """;
  }

  private PracticeSession session() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new PracticeSession(
        SESSION_ID,
        USER_ID,
        PLAN_ID,
        PHASE_INDEX,
        PROBLEM_SLUG,
        PracticeSessionStatus.ACTIVE,
        100L,
        200L,
        PracticeProgressStatus.IN_PROGRESS,
        null,
        now,
        now,
        "zh-CN");
  }

  private AgentStreamEvent.AgentRunEnd runEnd() {
    return new AgentStreamEvent.AgentRunEnd(
        "run-1",
        1,
        LlmFinishReason.STOP,
        Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, 501L));
  }

  private AgentStreamEvent.AgentRunEnd onlyRunEnd(List<AgentStreamEvent> events) {
    assertThat(events).singleElement().isInstanceOf(AgentStreamEvent.AgentRunEnd.class);
    return (AgentStreamEvent.AgentRunEnd) events.get(0);
  }

  private List<AgentStreamEvent> collect(Flow.Publisher<AgentStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    subscriber.await();
    assertThat(subscriber.error).isNull();
    assertThat(subscriber.completed).isTrue();
    return subscriber.events;
  }

  private AgentRequest reviewAgentRequest(String runId) {
    return new AgentRequest(
        runId,
        "request-" + runId,
        List.of(LlmMessage.user(plainPastedMergeCode())),
        Map.of(
            AgentRuntimeMetadataKeys.USER_ID, USER_ID,
            AgentRuntimeMetadataKeys.RUN_DB_ID, RUN_DB_ID,
            AgentRuntimeMetadataKeys.TASK_ID, TASK_ID,
            AgentRuntimeMetadataKeys.TURN_ID, TURN_ID,
            PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO,
            PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, SESSION_ID,
            PracticeChatPromptConstants.METADATA_PLAN_ID, PLAN_ID,
            PracticeChatPromptConstants.METADATA_PHASE_INDEX, PHASE_INDEX,
            PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, PROBLEM_SLUG));
  }

  private AgentLoopRunner practiceRunner(
      FakeGateway gateway,
      PracticeSessionRepository sessionRepository,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      PracticeCodeReviewService reviewService,
      InMemoryAgentToolPermissionCoordinator coordinator
  ) {
    PracticeCodeReviewAgentTool tool = new PracticeCodeReviewAgentTool(
        sessionRepository,
        turnMessageLookupRepository,
        reviewService,
        OBJECT_MAPPER);
    return new AgentLoopRunner(
        gateway,
        new LlmModelSelector(null, LlmModelId.of("gpt-test"), Set.of(), null),
        AgentToolRegistry.of(List.of(tool)),
        LlmToolChoice.auto(),
        4,
        List.of(),
        List.of(),
        ToolResultCompactionPolicy.defaults(),
        new org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore(),
        OBJECT_MAPPER,
        new AgentToolPermissionGuard(
            new AgentToolPermissionHookChain(List.of(new PracticeCodeReviewPermissionHook(
                sessionRepository,
                turnMessageLookupRepository))),
            coordinator));
  }

  private FakeGateway reviewToolGateway(String finalText) {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(new LlmToolCall(
            "call_review_1",
            PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
            JsonNodeFactory.instance.objectNode()
                .put(PracticeCodeReviewAgentToolNames.ARGUMENT_USER_INTENT, "请做正式 Review"))),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta(finalText),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    return gateway;
  }

  private InMemoryAgentToolPermissionCoordinator permissionCoordinator(Duration timeout) {
    return new InMemoryAgentToolPermissionCoordinator(
        new AgentToolPermissionResultFactory(OBJECT_MAPPER),
        timeout,
        Clock.systemUTC());
  }

  private AgentTurnMessages turnMessages() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    AgentMessage userMessage = new AgentMessage(
        USER_MESSAGE_ID,
        TASK_ID,
        1L,
        AgentMessage.Role.USER,
        plainPastedMergeCode(),
        now);
    AgentMessage assistantMessage = new AgentMessage(
        ASSISTANT_MESSAGE_ID,
        TASK_ID,
        2L,
        AgentMessage.Role.ASSISTANT,
        "我会先确认是否执行正式 Review。",
        now.plusSeconds(1));
    return new AgentTurnMessages(RUN_DB_ID, TURN_ID, userMessage, assistantMessage);
  }

  private LlmContentPart.ToolResult toolResultFromSecondLlmRequest(FakeGateway gateway) {
    assertThat(gateway.requests).hasSize(2);
    return (LlmContentPart.ToolResult) gateway.requests.get(1).messages().get(2).content().get(0);
  }

  private AgentStreamEvent.AgentToolEnd onlyToolEnd(List<AgentStreamEvent> events) {
    List<AgentStreamEvent.AgentToolEnd> toolEnds = events.stream()
        .filter(AgentStreamEvent.AgentToolEnd.class::isInstance)
        .map(AgentStreamEvent.AgentToolEnd.class::cast)
        .toList();
    assertThat(toolEnds).hasSize(1);
    return toolEnds.get(0);
  }

  private static PracticeCodeReviewDraft reviewedDraft(PracticeTurnContext context) {
    return new PracticeCodeReviewDraft(
        context.userId(),
        context.planId(),
        context.phaseIndex(),
        context.problemSlug(),
        context.sessionId(),
        context.userMessageId(),
        context.assistantMessageId(),
        context.agentRunDbId(),
        context.originalMessage(),
        context.originalMessage(),
        "java",
        List.of(new PracticeCodeReviewEvidence("FLOW_TEST", "permission allowed")),
        "权限允许后保存的 Review。",
        new PracticeCodeReviewScore(
            new BigDecimal("3.0"),
            new BigDecimal("2.0"),
            new BigDecimal("1.0"),
            new BigDecimal("1.0"),
            new BigDecimal("1.0"),
            new BigDecimal("8.0")),
        true,
        List.of("边界用例还可以补充。"),
        List.of("补充空数组与长度差异较大的用例。"),
        "整体可通过。");
  }

  private final class InMemoryPracticeSessionRepository implements PracticeSessionRepository {

    @Override
    public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
      throw new UnsupportedOperationException("upsert progress not used");
    }

    @Override
    public PracticeSession upsertAndLockSession(
        long userId,
        long planId,
        int phaseIndex,
        String problemSlug,
        String locale
    ) {
      throw new UnsupportedOperationException("upsert session not used");
    }

    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      return Optional.of(session())
          .filter(value -> value.id() == sessionId && value.userId() == userId);
    }

    @Override
    public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      throw new UnsupportedOperationException("attach task not used");
    }

    @Override
    public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      throw new UnsupportedOperationException("attach seed not used");
    }

    @Override
    public PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status) {
      throw new UnsupportedOperationException("update progress not used");
    }

    @Override
    public void touchLastMessageAt(long sessionId) {
    }
  }

  private static final class InMemoryReviewRepository implements PracticeCodeReviewRepository {
    private final List<PracticeCodeReviewDraft> savedDrafts = new ArrayList<>();
    private PracticeCodeReview latest;

    @Override
    public PracticeCodeReview save(PracticeCodeReviewDraft draft) {
      savedDrafts.add(draft);
      latest = toReview(draft);
      return latest;
    }

    @Override
    public Optional<PracticeCodeReviewSummary> findLatestSummary(long userId, long sessionId) {
      return Optional.ofNullable(latest)
          .filter(review -> review.userId() == userId && review.sessionId() == sessionId)
          .map(review -> new PracticeCodeReviewSummary(
              review.id(),
              review.versionNo(),
              review.language(),
              review.score().total(),
              review.passed(),
              review.createdAt()));
    }

    @Override
    public Optional<PracticeCodeReview> findLatest(long userId, long sessionId) {
      return Optional.ofNullable(latest)
          .filter(review -> review.userId() == userId && review.sessionId() == sessionId);
    }

    @Override
    public List<PracticeCodeReviewSummary> findSummaries(long userId, long sessionId) {
      return findLatestSummary(userId, sessionId).stream().toList();
    }

    @Override
    public Optional<PracticeCodeReview> findById(long userId, long sessionId, long reviewId) {
      return findLatest(userId, sessionId).filter(review -> review.id() == reviewId);
    }

    @Override
    public Optional<PracticeCodeReview> findByUserMessage(long userId, long sessionId, long userMessageId) {
      return Optional.ofNullable(latest)
          .filter(review -> review.userId() == userId
              && review.sessionId() == sessionId
              && review.userMessageId() == userMessageId);
    }

    private static PracticeCodeReview toReview(PracticeCodeReviewDraft draft) {
      return new PracticeCodeReview(
          900L,
          draft.userId(),
          draft.planId(),
          draft.phaseIndex(),
          draft.problemSlug(),
          draft.sessionId(),
          1,
          draft.userMessageId(),
          draft.assistantMessageId(),
          draft.agentRunDbId(),
          draft.rawCode(),
          draft.normalizedCode(),
          draft.language(),
          draft.evidence(),
          draft.contextSummary(),
          draft.score(),
          draft.passed(),
          draft.deductionReasons(),
          draft.improvementSuggestions(),
          draft.reviewMarkdown(),
          Instant.parse("2026-01-01T00:00:00Z"));
    }
  }

  private static final class InMemoryTurnMessageLookupRepository implements AgentTurnMessageLookupRepository {
    private final AgentTurnMessages messages;

    private InMemoryTurnMessageLookupRepository(AgentTurnMessages messages) {
      this.messages = messages;
    }

    @Override
    public Optional<AgentTurnMessages> findByRunId(long runId) {
      return messages.runId() == runId ? Optional.of(messages) : Optional.empty();
    }
  }

  private static final class SavingReviewService extends PracticeCodeReviewService {
    private SavingReviewService(InMemoryReviewRepository repository) {
      super(context -> PracticeReviewResult.saved(repository.save(reviewedDraft(context))));
    }
  }

  private static final class FakeGateway implements LlmGateway {
    private final List<LlmCompletionRequest> requests = new ArrayList<>();
    private final List<List<LlmStreamEvent>> steps = new ArrayList<>();

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("review flow should use stream calls");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      requests.add(request);
      List<LlmStreamEvent> events = steps.remove(0);
      return subscriber -> {
        SubmissionPublisher<LlmStreamEvent> publisher = new SubmissionPublisher<>();
        publisher.subscribe(subscriber);
        events.forEach(publisher::submit);
        publisher.close();
      };
    }
  }

  private final class PermissionDecisionSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    private final List<AgentStreamEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final CountDownLatch permissionRequested = new CountDownLatch(1);
    private final CountDownLatch done = new CountDownLatch(1);
    private final AtomicReference<AgentStreamEvent.ToolPermissionRequest> request = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent item) {
      events.add(item);
      if (item instanceof AgentStreamEvent.ToolPermissionRequest permissionRequest) {
        request.set(permissionRequest);
        permissionRequested.countDown();
      }
    }

    @Override
    public void onError(Throwable throwable) {
      error.set(throwable);
      done.countDown();
    }

    @Override
    public void onComplete() {
      done.countDown();
    }

    private AgentStreamEvent.ToolPermissionRequest awaitPermissionRequest() {
      await(permissionRequested);
      return request.get();
    }

    private List<AgentStreamEvent> awaitCompletion() {
      await(done);
      assertThat(error.get()).isNull();
      return List.copyOf(events);
    }

    private void await(CountDownLatch latch) {
      try {
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError(ex);
      }
    }
  }

  private static final class CapturingCoordinator extends AgentConversationRunCoordinator {
    private final AgentStreamEvent.AgentRunEnd runEnd;

    private CapturingCoordinator(AgentStreamEvent.AgentRunEnd runEnd) {
      super(
          new AgentConversationService(new UnusedConversationRepository(), new ContextAssembler()),
          new UnusedAgentLoopRunner(),
          new InMemoryAgentRunLockManager(),
          new LocalAgentRunLockOwnerProvider("owner-a"));
      this.runEnd = runEnd;
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentConversationCommand command) {
      return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
        private boolean completed;

        @Override
        public void request(long n) {
          if (completed || n <= 0) {
            return;
          }
          completed = true;
          subscriber.onNext(runEnd);
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
    private final CountDownLatch done = new CountDownLatch(1);
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
      done.countDown();
    }

    @Override
    public void onComplete() {
      completed = true;
      done.countDown();
    }

    private void await() {
      try {
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError(ex);
      }
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
      throw new UnsupportedOperationException("create run not used");
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
