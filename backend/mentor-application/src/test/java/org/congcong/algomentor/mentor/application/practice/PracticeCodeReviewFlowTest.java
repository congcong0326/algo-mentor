package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationCommand;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunCoordinator;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationService;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewFlowTest {

  private static final long USER_ID = 7L;
  private static final long SESSION_ID = 50L;
  private static final long PLAN_ID = 12L;
  private static final int PHASE_INDEX = 1;
  private static final String PROBLEM_SLUG = "merge-sorted-array";
  private static final long RUN_DB_ID = 501L;
  private static final long USER_MESSAGE_ID = 701L;
  private static final long ASSISTANT_MESSAGE_ID = 702L;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void plainPastedMergeSortedArrayCodeSavesReviewAndOpensCompletionGate() {
    InMemoryReviewRepository reviewRepository = new InMemoryReviewRepository();
    FakeLlmGateway llmGateway = new FakeLlmGateway(reviewOutput(true, true, true));
    PracticeMessageStreamService streamService = streamService(reviewRepository, llmGateway, plainPastedMergeCode());

    List<AgentStreamEvent> events = collect(streamService.stream(
        USER_ID,
        SESSION_ID,
        plainPastedMergeCode(),
        "idem-plain-code",
        "zh-CN",
        Map.of("requestId", "req-1")));

    AgentStreamEvent.AgentRunEnd runEnd = onlyRunEnd(events);
    assertThat(codeReviewMetadata(runEnd))
        .containsEntry("status", PracticeReviewStatus.SAVED.name())
        .containsEntry("totalScore", "8.0")
        .containsEntry("passed", true)
        .containsEntry("language", "java");
    assertThat(llmGateway.completeCalls).isEqualTo(1);
    assertThat(reviewRepository.savedDrafts).hasSize(1);
    assertThat(reviewRepository.savedDrafts.get(0).rawCode()).contains("public void merge");

    PracticeCompletionGate gate = new PracticeCompletionGateService(reviewRepository)
        .evaluate(USER_ID, session());
    assertThat(gate.canComplete()).isTrue();
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.PASSED);
  }

  @Test
  void reviewAttemptRejectedByLlmIsObservableInRunEndMetadata() {
    InMemoryReviewRepository reviewRepository = new InMemoryReviewRepository();
    FakeLlmGateway llmGateway = new FakeLlmGateway(reviewOutput(true, true, false));
    PracticeMessageStreamService streamService = streamService(reviewRepository, llmGateway, plainPastedMergeCode());

    List<AgentStreamEvent> events = collect(streamService.stream(
        USER_ID,
        SESSION_ID,
        plainPastedMergeCode(),
        "idem-rejected-code",
        "zh-CN",
        Map.of()));

    assertThat(codeReviewMetadata(onlyRunEnd(events)))
        .containsEntry("status", PracticeReviewStatus.SAVED.name())
        .containsEntry("reviewAttemptStatus", PracticeReviewStatus.NOT_COMPLETE_SUBMISSION.name())
        .containsEntry("totalScore", "0")
        .containsEntry("passed", false)
        .containsEntry("codeSubmissionCandidate", true);
    assertThat(llmGateway.completeCalls).isEqualTo(1);
    assertThat(reviewRepository.savedDrafts).hasSize(1);

    PracticeCompletionGate gate = new PracticeCompletionGateService(reviewRepository)
        .evaluate(USER_ID, session());
    assertThat(gate.reasonCode()).isEqualTo(PracticeCompletionGate.ReasonCode.LATEST_REVIEW_FAILED);
    assertThat(gate.latestScore()).contains(BigDecimal.ZERO);
  }

  private PracticeMessageStreamService streamService(
      InMemoryReviewRepository reviewRepository,
      FakeLlmGateway llmGateway,
      String userMessage
  ) {
    PracticeCodeReviewService reviewService = new PracticeCodeReviewService(
        reviewRepository,
        llmGateway,
        new PracticeCodeReviewPromptBuilder(),
        new PracticeCodeReviewStructuredOutputMapper());
    PracticeTurnOrchestrator orchestrator = new PracticeTurnOrchestrator(
        new InMemoryPracticeSessionRepository(),
        new CapturingCoordinator(runEnd()),
        new FixedTurnMessageLookupRepository(messages(userMessage)),
        new PracticeTurnClassifier(),
        new PracticeTurnCapabilityRegistry(List.of(new CodeReviewTurnCapability(reviewService))),
        this::problemDetail);
    return new PracticeMessageStreamService(new InMemoryPracticeSessionRepository(), orchestrator);
  }

  private Optional<PracticeChatProblemDetail> problemDetail(String slug, String locale) {
    return Optional.of(new PracticeChatProblemDetail(
        slug,
        88,
        "合并两个有序数组",
        "EASY",
        List.of("数组", "双指针"),
        "将 nums2 合并到 nums1 中，使 nums1 成为一个有序数组。",
        "https://leetcode.com/problems/merge-sorted-array/"));
  }

  private JsonNode reviewOutput(boolean isCodeSubmission, boolean belongsToCurrentProblem, boolean isComplete) {
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("isCodeSubmission", isCodeSubmission);
    output.put("belongsToCurrentProblem", belongsToCurrentProblem);
    output.put("isCompleteLeetCodeSolution", isComplete);
    output.put("language", "java");
    output.put("rawCode", plainPastedMergeCode());
    output.put("normalizedCode", plainPastedMergeCode());
    output.put("evidence", List.of(Map.of("type", "ENTRY_FUNCTION", "value", "merge")));
    output.put("contextSummary", "用户提交了 merge-sorted-array 的 Java 解法。");
    output.put("scores", Map.of(
        "correctness", 3.0,
        "complexity", 1.0,
        "edgeCases", 2.0,
        "codeQuality", 1.0,
        "problemFit", 1.0,
        "total", 8.0));
    output.put("passed", true);
    output.put("deductionReasons", List.of("使用了额外数组，空间复杂度不是最优。"));
    output.put("improvementSuggestions", List.of("可以从后往前原地合并。"));
    output.put("reviewMarkdown", "代码可以得到正确结果，但空间复杂度可以优化。");
    return objectMapper.valueToTree(output);
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

  private AgentTurnMessages messages(String userMessage) {
    return new AgentTurnMessages(
        RUN_DB_ID,
        601L,
        message(USER_MESSAGE_ID, AgentMessage.Role.USER, userMessage),
        message(ASSISTANT_MESSAGE_ID, AgentMessage.Role.ASSISTANT, "可以，我来 review。"));
  }

  private AgentMessage message(long id, AgentMessage.Role role, String content) {
    return new AgentMessage(id, 100L, id, role, content, Instant.parse("2026-01-01T00:00:00Z"), Map.of());
  }

  private AgentStreamEvent.AgentRunEnd runEnd() {
    return new AgentStreamEvent.AgentRunEnd(
        "run-1",
        1,
        LlmFinishReason.STOP,
        Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, RUN_DB_ID));
  }

  private AgentStreamEvent.AgentRunEnd onlyRunEnd(List<AgentStreamEvent> events) {
    assertThat(events).singleElement().isInstanceOf(AgentStreamEvent.AgentRunEnd.class);
    return (AgentStreamEvent.AgentRunEnd) events.get(0);
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

  private List<AgentStreamEvent> collect(Flow.Publisher<AgentStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    assertThat(subscriber.error).isNull();
    assertThat(subscriber.completed).isTrue();
    return subscriber.events;
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

  private static final class FakeLlmGateway implements LlmGateway {
    private final JsonNode output;
    private int completeCalls;

    private FakeLlmGateway(JsonNode output) {
      this.output = output;
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      completeCalls++;
      return new LlmCompletionResult(
          LlmMessage.assistant("{}"),
          List.of(),
          output,
          LlmFinishReason.STOP,
          null,
          new LlmProviderId("fake"),
          new LlmModelId("fake-model"),
          Map.of());
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("stream not used");
    }
  }

  private static final class FixedTurnMessageLookupRepository implements AgentTurnMessageLookupRepository {
    private final AgentTurnMessages messages;

    private FixedTurnMessageLookupRepository(AgentTurnMessages messages) {
      this.messages = messages;
    }

    @Override
    public Optional<AgentTurnMessages> findByRunId(long runId) {
      return Optional.of(messages).filter(value -> value.runId() == runId);
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
