package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentOutput;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.publisher.QueuePublisher;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void savesCompleteSubmission() {
    FakeRepository repository = new FakeRepository();
    FakeAgentRuntime runtime = new FakeAgentRuntime(structuredOutput(true, true, true));
    RecordingPracticeCodeReviewMetrics metrics = new RecordingPracticeCodeReviewMetrics();
    PracticeCodeReviewService service = service(repository, runtime, metrics);

    PracticeReviewResult result = service.review(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.SAVED);
    assertThat(result.draft()).isEmpty();
    assertThat(repository.savedDrafts).hasSize(1);
    assertThat(repository.savedDrafts.get(0).rawCode()).contains("class Solution");
    assertThat(runtime.executeCalls).isEqualTo(1);
    assertThat(runtime.lastInvocation).satisfies(invocation -> {
      assertThat(invocation.agentKey()).isEqualTo(PracticeCodeReviewAgentDefinition.KEY);
      assertThat(invocation.context().parentRunId()).isEqualTo("501");
      assertThat(invocation.context().parentStepIndex()).isEqualTo(1);
      assertThat(invocation.context().idempotencyKey()).isEqualTo("practice-code-review:50:701");
    });
    assertThat(metrics.reviewStatuses).containsExactly(PracticeCodeReviewMetricStatus.COMPLETED);
  }

  @Test
  void nonCurrentProblemDoesNotPersistAFormalReview() {
    FakeRepository repository = new FakeRepository();
    FakeAgentRuntime runtime = new FakeAgentRuntime(structuredOutput(true, false, true));
    RecordingPracticeCodeReviewMetrics metrics = new RecordingPracticeCodeReviewMetrics();
    PracticeCodeReviewService service = service(repository, runtime, metrics);

    PracticeReviewResult result = service.review(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.NOT_COMPLETE_SUBMISSION);
    assertThat(repository.savedDrafts).isEmpty();
    assertThat(runtime.executeCalls).isEqualTo(1);
    assertThat(metrics.reviewStatuses).containsExactly(PracticeCodeReviewMetricStatus.UNREVIEWABLE);
  }

  @Test
  void replayReturnsExistingReviewWithoutCallingLlm() {
    FakeRepository repository = new FakeRepository();
    repository.existing = Optional.of(review());
    FakeAgentRuntime runtime = new FakeAgentRuntime(structuredOutput(true, true, true));
    PracticeCodeReviewService service = service(repository, runtime);

    PracticeReviewResult result = service.review(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.SAVED);
    assertThat(result.draft()).isEmpty();
    assertThat(result.metadata()).containsEntry("reviewId", 900L);
    assertThat(repository.savedDrafts).isEmpty();
    assertThat(runtime.executeCalls).isZero();
  }

  @Test
  void readsAtMostFourCrossPlanHistoryFactsOnlyAfterTheIdempotencyMiss() {
    FakeRepository repository = new FakeRepository();
    FakeAgentRuntime runtime = new FakeAgentRuntime(structuredOutput(true, true, true));
    CountingHistoryRepository historyRepository = new CountingHistoryRepository(List.of(
        new PracticeCodeReviewHistoricalFact(90L, false, "遗漏初始化", Instant.parse("2026-01-01T00:00:00Z")),
        new PracticeCodeReviewHistoricalFact(91L, true, null, Instant.parse("2026-01-02T00:00:00Z"))));
    PracticeCodeReviewService service = service(repository, runtime, historyRepository);

    PracticeReviewResult result = service.review(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.SAVED);
    assertThat(historyRepository.calls).isEqualTo(1);
    assertThat(historyRepository.userId).isEqualTo(7L);
    assertThat(historyRepository.problemSlug).isEqualTo("climbing-stairs");
    assertThat(historyRepository.limit).isEqualTo(4);
    PracticeCodeReviewAgentInput input = (PracticeCodeReviewAgentInput) runtime.lastInvocation.input();
    assertThat(input.historicalReviews()).extracting(PracticeCodeReviewHistoricalFact::reviewId)
        .containsExactly(90L, 91L);
  }

  @Test
  void idempotencyHitDoesNotReadHistory() {
    FakeRepository repository = new FakeRepository();
    repository.existing = Optional.of(review());
    FakeAgentRuntime runtime = new FakeAgentRuntime(structuredOutput(true, true, true));
    CountingHistoryRepository historyRepository = new CountingHistoryRepository(List.of());
    PracticeCodeReviewService service = service(repository, runtime, historyRepository);

    service.review(context());

    assertThat(historyRepository.calls).isZero();
    assertThat(runtime.executeCalls).isZero();
  }

  @Test
  void historyLookupFailureStillSavesCurrentReviewWithACurrentConclusionFallback() {
    FakeRepository repository = new FakeRepository();
    ObjectNode output = (ObjectNode) structuredOutput(true, true, true);
    output.put(PracticeCodeReviewConstants.JSON_REVIEW_HISTORY_SUMMARY, "此前存在问题；当前版本通过。");
    FakeAgentRuntime runtime = new FakeAgentRuntime(output);
    PracticeCodeReviewHistoryRepository failingHistory = (userId, problemSlug, limit) -> {
      throw new IllegalStateException("database unavailable");
    };
    PracticeCodeReviewService service = service(repository, runtime, failingHistory);

    PracticeReviewResult result = service.review(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.SAVED);
    assertThat(repository.savedDrafts).singleElement().satisfies(draft ->
        assertThat(draft.reviewHistorySummary()).isEqualTo("本次 Review 结论：提交的解法预计可以通过。"));
  }

  @Test
  void replayMissingExistingReviewFailsWithoutCallingLlm() {
    FakeRepository repository = new FakeRepository();
    FakeAgentRuntime runtime = new FakeAgentRuntime(structuredOutput(true, true, true));
    RecordingPracticeCodeReviewMetrics metrics = new RecordingPracticeCodeReviewMetrics();
    PracticeCodeReviewService service = service(repository, runtime, metrics);

    PracticeReviewResult result = service.replay(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(PracticeCodeReviewService.FAILURE_CODE_REPLAY_REVIEW_MISSING);
    assertThat(repository.savedDrafts).isEmpty();
    assertThat(runtime.executeCalls).isZero();
    assertThat(metrics.reviewStatuses).containsExactly(PracticeCodeReviewMetricStatus.FAILED);
  }

  @Test
  void llmFailureReturnsFailed() {
    FakeRepository repository = new FakeRepository();
    FakeAgentRuntime runtime = new FakeAgentRuntime(structuredOutput(true, true, true));
    runtime.failure = new RuntimeException("provider unavailable");
    RecordingPracticeCodeReviewMetrics metrics = new RecordingPracticeCodeReviewMetrics();
    PracticeCodeReviewService service = service(repository, runtime, metrics);

    PracticeReviewResult result = service.review(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(PracticeCodeReviewService.FAILURE_CODE_LLM_COMPLETION_FAILED);
    assertThat(repository.savedDrafts).isEmpty();
    assertThat(runtime.executeCalls).isEqualTo(1);
    assertThat(metrics.reviewStatuses).containsExactly(PracticeCodeReviewMetricStatus.FAILED);
  }

  private PracticeCodeReviewService service(FakeRepository repository, FakeAgentRuntime runtime) {
    return service(repository, runtime, PracticeCodeReviewMetrics.NOOP);
  }

  private PracticeCodeReviewService service(
      FakeRepository repository,
      FakeAgentRuntime runtime,
      PracticeCodeReviewMetrics metrics) {
    return new PracticeCodeReviewService(
        repository,
        commitService(repository),
        runtime,
        new PracticeCodeReviewStructuredOutputMapper(),
        metrics,
        org.congcong.algomentor.mentor.application.review.card.PracticeCodeReviewObserver.NOOP);
  }

  private PracticeCodeReviewService service(
      FakeRepository repository,
      FakeAgentRuntime runtime,
      PracticeCodeReviewHistoryRepository historyRepository
  ) {
    return new PracticeCodeReviewService(
        repository,
        commitService(repository),
        runtime,
        new PracticeCodeReviewStructuredOutputMapper(),
        historyRepository,
        PracticeCodeReviewMetrics.NOOP,
        org.congcong.algomentor.mentor.application.review.card.PracticeCodeReviewObserver.NOOP);
  }

  private PracticeCodeReviewCommitService commitService(PracticeCodeReviewRepository repository) {
    QueuePublisher publisher = (topic, key, payload) -> new QueueMessage(1L, topic, key, "{}", Instant.EPOCH);
    return new PracticeCodeReviewCommitService(repository, publisher);
  }

  private PracticeTurnContext context() {
    return new PracticeTurnContext(
        7L,
        12L,
        1,
        "climbing-stairs",
        50L,
        701L,
        702L,
        501L,
        "Climbing Stairs",
        "动态规划入门阶段",
        "class Solution { public int climbStairs(int n) { return n; } }",
        "请 review 我的代码",
        "最近在讨论递推定义。",
        "zh-CN");
  }

  static PracticeCodeReview review() {
    return new PracticeCodeReview(
        900L,
        7L,
        12L,
        1,
        "climbing-stairs",
        50L,
        3,
        701L,
        702L,
        501L,
        "class Solution { public int climbStairs(int n) { return n; } }",
        "class Solution { public int climbStairs(int n) { return n; } }",
        "java",
        List.of(new PracticeCodeReviewEvidence("ENTRY_FUNCTION", "climbStairs")),
        "已保存的 Review",
        new PracticeCodeReviewScore(
            new BigDecimal("3.0"),
            new BigDecimal("2.0"),
            new BigDecimal("1.0"),
            new BigDecimal("1.0"),
            new BigDecimal("1.0"),
            new BigDecimal("8.0")),
        true,
        List.of("边界覆盖不足"),
        List.of("补充 n=1 的处理"),
        "整体可通过。",
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static PracticeCodeReview review(PracticeCodeReviewDraft draft) {
    return new PracticeCodeReview(
        900L,
        draft.userId(),
        draft.planId(),
        draft.phaseIndex(),
        draft.problemSlug(),
        draft.sessionId(),
        3,
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

  private JsonNode structuredOutput(boolean isCodeSubmission, boolean belongsToCurrentProblem,
      boolean isCompleteLeetCodeSolution) {
    Map<String, Object> output = new LinkedHashMap<>();
    output.put("isCodeSubmission", isCodeSubmission);
    output.put("belongsToCurrentProblem", belongsToCurrentProblem);
    output.put("isCompleteLeetCodeSolution", isCompleteLeetCodeSolution);
    output.put("language", "java");
    output.put("rawCode", "class Solution { public int climbStairs(int n) { return n; } }");
    output.put("normalizedCode", "class Solution { public int climbStairs(int n) { return n; } }");
    output.put("evidence", List.of(Map.of("type", "ENTRY_FUNCTION", "value", "climbStairs")));
    output.put("contextSummary", "用户提交了 Java 解法。");
    output.put(PracticeCodeReviewConstants.JSON_JUDGE_ASSESSMENT, Map.of(
        PracticeCodeReviewConstants.JSON_JUDGE_VERDICT, "LIKELY_ACCEPTED",
        PracticeCodeReviewConstants.JSON_VERDICT_BASIS, "STATIC_ANALYSIS",
        PracticeCodeReviewConstants.JSON_BLOCKING_ISSUE, false,
        PracticeCodeReviewConstants.JSON_MEETS_EXPECTED_COMPLEXITY, true,
        PracticeCodeReviewConstants.JSON_TIME_COMPLEXITY, "O(n)",
        PracticeCodeReviewConstants.JSON_SPACE_COMPLEXITY, "O(1)",
        PracticeCodeReviewConstants.JSON_EXPECTED_TIME_COMPLEXITY, "O(n)",
        PracticeCodeReviewConstants.JSON_CONSTRAINT_ANALYSIS, "最大约束下预计可以通过。"));
    output.put(PracticeCodeReviewConstants.JSON_SCORE_EXPLANATIONS, Map.of(
        PracticeCodeReviewConstants.JSON_SCORE_CORRECTNESS, "核心逻辑基本正确。",
        PracticeCodeReviewConstants.JSON_SCORE_COMPLEXITY, "达到题目预期复杂度。",
        PracticeCodeReviewConstants.JSON_SCORE_EDGE_CASES, "边界覆盖仍有遗漏。",
        PracticeCodeReviewConstants.JSON_SCORE_CODE_QUALITY, "代码结构清晰。",
        PracticeCodeReviewConstants.JSON_SCORE_PROBLEM_FIT, "满足当前题目的主要要求。"));
    output.put("scores", Map.of(
            "correctness", 3.0,
            "complexity", 2.0,
            "edgeCases", 1.0,
            "codeQuality", 1.0,
            "problemFit", 1.0,
            "total", 8.0));
    output.put("passed", true);
    output.put("deductionReasons", List.of("边界覆盖不足"));
    output.put("improvementSuggestions", List.of("补充 n=1 的处理"));
    output.put("reviewMarkdown", "整体可通过。");
    output.put(PracticeCodeReviewConstants.JSON_AFFECTED_TAG_IDS, List.of());
    return objectMapper.valueToTree(output);
  }

  private static final class FakeRepository implements PracticeCodeReviewRepository {
    private final List<PracticeCodeReviewDraft> savedDrafts = new ArrayList<>();
    private Optional<PracticeCodeReview> existing = Optional.empty();

    @Override
    public PracticeCodeReview save(PracticeCodeReviewDraft draft) {
      savedDrafts.add(draft);
      return review(draft);
    }

    @Override
    public Optional<PracticeCodeReviewSummary> findLatestSummary(long userId, long sessionId) {
      return Optional.empty();
    }

    @Override
    public Optional<PracticeCodeReview> findLatest(long userId, long sessionId) {
      return Optional.empty();
    }

    @Override
    public List<PracticeCodeReviewSummary> findSummaries(long userId, long sessionId) {
      return List.of();
    }

    @Override
    public Optional<PracticeCodeReview> findById(long userId, long sessionId, long reviewId) {
      return Optional.empty();
    }

    @Override
    public Optional<PracticeCodeReview> findByUserMessage(long userId, long sessionId, long userMessageId) {
      return existing;
    }
  }

  private static final class RecordingPracticeCodeReviewMetrics implements PracticeCodeReviewMetrics {

    private final List<PracticeCodeReviewMetricStatus> reviewStatuses = new ArrayList<>();

    @Override
    public void recordReview(PracticeCodeReviewMetricStatus status) {
      reviewStatuses.add(status);
    }
  }

  private static final class FakeAgentRuntime implements AgentRuntime {
    private final JsonNode output;
    private RuntimeException failure;
    private int executeCalls;
    private AgentInvocation<?> lastInvocation;

    private FakeAgentRuntime(JsonNode output) {
      this.output = output;
    }

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      executeCalls++;
      lastInvocation = invocation;
      if (failure != null) {
        throw failure;
      }
      return new AgentRunResult(
          1,
          LlmFinishReason.STOP,
          new AgentOutput("{}", output, PracticeCodeReviewConstants.SCHEMA_NAME,
              PracticeCodeReviewConstants.SCHEMA_VERSION, Map.of()),
          Map.of(
              AgentRuntimeMetadataKeys.RUNTIME_PROVIDER, "review-provider",
              AgentRuntimeMetadataKeys.RUNTIME_MODEL, "review-model",
              AgentRuntimeMetadataKeys.RUNTIME_USAGE, new LlmUsage(11, 7, 0, 0, 18)));
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("stream not used");
    }
  }

  private static final class CountingHistoryRepository implements PracticeCodeReviewHistoryRepository {
    private final List<PracticeCodeReviewHistoricalFact> result;
    private int calls;
    private long userId;
    private String problemSlug;
    private int limit;

    private CountingHistoryRepository(List<PracticeCodeReviewHistoricalFact> result) {
      this.result = result;
    }

    @Override
    public List<PracticeCodeReviewHistoricalFact> findRecentForProblem(long userId, String problemSlug, int limit) {
      calls++;
      this.userId = userId;
      this.problemSlug = problemSlug;
      this.limit = limit;
      return result;
    }
  }
}
