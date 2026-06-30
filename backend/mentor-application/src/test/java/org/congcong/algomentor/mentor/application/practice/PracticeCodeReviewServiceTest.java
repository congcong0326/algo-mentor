package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmCapability;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void savesCompleteSubmission() {
    FakeRepository repository = new FakeRepository();
    FakeLlmGateway llmGateway = new FakeLlmGateway(structuredOutput(true, true, true));
    RecordingPracticeCodeReviewMetrics metrics = new RecordingPracticeCodeReviewMetrics();
    PracticeCodeReviewService service = service(repository, llmGateway, metrics);

    PracticeReviewResult result = service.review(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.SAVED);
    assertThat(result.draft()).isEmpty();
    assertThat(repository.savedDrafts).hasSize(1);
    assertThat(repository.savedDrafts.get(0).rawCode()).contains("class Solution");
    assertThat(llmGateway.completeCalls).isEqualTo(1);
    assertThat(llmGateway.lastRequest.modelSelector().requiredCapabilities())
        .contains(LlmCapability.JSON_SCHEMA_OUTPUT);
    assertThat(llmGateway.lastRequest.responseFormat())
        .isInstanceOfSatisfying(LlmResponseFormat.JsonSchema.class, schema -> {
          assertThat(schema.name()).isEqualTo(PracticeCodeReviewConstants.SCHEMA_NAME);
          assertThat(schema.strict()).isTrue();
        });
    assertThat(llmGateway.lastRequest.metadata())
        .containsEntry(PracticeCodeReviewConstants.METADATA_REVIEW_CANDIDATE, true)
        .containsEntry(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, 50L);
    assertThat(metrics.reviewStatuses).containsExactly(PracticeCodeReviewMetricStatus.COMPLETED);
  }

  @Test
  void nonCurrentProblemSavesRejectedAttempt() {
    FakeRepository repository = new FakeRepository();
    FakeLlmGateway llmGateway = new FakeLlmGateway(structuredOutput(true, false, true));
    RecordingPracticeCodeReviewMetrics metrics = new RecordingPracticeCodeReviewMetrics();
    PracticeCodeReviewService service = service(repository, llmGateway, metrics);

    PracticeReviewResult result = service.review(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.SAVED);
    assertThat(result.metadata())
        .containsEntry("reviewAttemptStatus", PracticeReviewStatus.NOT_COMPLETE_SUBMISSION.name())
        .containsEntry("totalScore", "0")
        .containsEntry("passed", false);
    assertThat(repository.savedDrafts).hasSize(1);
    assertThat(repository.savedDrafts.get(0).score().total()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(repository.savedDrafts.get(0).passed()).isFalse();
    assertThat(llmGateway.completeCalls).isEqualTo(1);
    assertThat(metrics.reviewStatuses).containsExactly(PracticeCodeReviewMetricStatus.UNREVIEWABLE);
  }

  @Test
  void replayReturnsExistingReviewWithoutCallingLlm() {
    FakeRepository repository = new FakeRepository();
    repository.existing = Optional.of(review());
    FakeLlmGateway llmGateway = new FakeLlmGateway(structuredOutput(true, true, true));
    PracticeCodeReviewService service = service(repository, llmGateway);

    PracticeReviewResult result = service.review(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.SAVED);
    assertThat(result.draft()).isEmpty();
    assertThat(result.metadata()).containsEntry("reviewId", 900L);
    assertThat(repository.savedDrafts).isEmpty();
    assertThat(llmGateway.completeCalls).isZero();
  }

  @Test
  void replayMissingExistingReviewFailsWithoutCallingLlm() {
    FakeRepository repository = new FakeRepository();
    FakeLlmGateway llmGateway = new FakeLlmGateway(structuredOutput(true, true, true));
    RecordingPracticeCodeReviewMetrics metrics = new RecordingPracticeCodeReviewMetrics();
    PracticeCodeReviewService service = service(repository, llmGateway, metrics);

    PracticeReviewResult result = service.replay(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo(PracticeCodeReviewService.FAILURE_CODE_REPLAY_REVIEW_MISSING);
    assertThat(repository.savedDrafts).isEmpty();
    assertThat(llmGateway.completeCalls).isZero();
    assertThat(metrics.reviewStatuses).containsExactly(PracticeCodeReviewMetricStatus.FAILED);
  }

  @Test
  void llmFailureReturnsFailed() {
    FakeRepository repository = new FakeRepository();
    FakeLlmGateway llmGateway = new FakeLlmGateway(structuredOutput(true, true, true));
    llmGateway.failure = new RuntimeException("provider unavailable");
    RecordingPracticeCodeReviewMetrics metrics = new RecordingPracticeCodeReviewMetrics();
    PracticeCodeReviewService service = service(repository, llmGateway, metrics);

    PracticeReviewResult result = service.review(context());

    assertThat(result.status()).isEqualTo(PracticeReviewStatus.SAVED);
    assertThat(result.metadata())
        .containsEntry("reviewAttemptStatus", PracticeReviewStatus.FAILED.name())
        .containsEntry("reviewAttemptFailureCode", PracticeCodeReviewService.FAILURE_CODE_LLM_COMPLETION_FAILED)
        .containsEntry("totalScore", "0")
        .containsEntry("passed", false);
    assertThat(repository.savedDrafts).hasSize(1);
    PracticeCodeReviewDraft savedDraft = repository.savedDrafts.get(0);
    assertThat(savedDraft.language()).isEqualTo("unknown");
    assertThat(savedDraft.score().total()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(savedDraft.passed()).isFalse();
    assertThat(savedDraft.evidence())
        .contains(new PracticeCodeReviewEvidence("REVIEW_ATTEMPT_REJECTED", PracticeReviewStatus.FAILED.name()));
    assertThat(savedDraft.reviewMarkdown()).contains(PracticeCodeReviewService.FAILURE_CODE_LLM_COMPLETION_FAILED);
    assertThat(llmGateway.completeCalls).isEqualTo(1);
    assertThat(metrics.reviewStatuses).containsExactly(PracticeCodeReviewMetricStatus.FAILED);
  }

  private PracticeCodeReviewService service(FakeRepository repository, FakeLlmGateway llmGateway) {
    return service(repository, llmGateway, PracticeCodeReviewMetrics.NOOP);
  }

  private PracticeCodeReviewService service(
      FakeRepository repository,
      FakeLlmGateway llmGateway,
      PracticeCodeReviewMetrics metrics) {
    return new PracticeCodeReviewService(
        repository,
        llmGateway,
        new PracticeCodeReviewPromptBuilder(),
        new PracticeCodeReviewStructuredOutputMapper(),
        metrics);
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

  private static final class FakeLlmGateway implements LlmGateway {
    private final JsonNode output;
    private RuntimeException failure;
    private int completeCalls;
    private LlmCompletionRequest lastRequest;

    private FakeLlmGateway(JsonNode output) {
      this.output = output;
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      completeCalls++;
      lastRequest = request;
      if (failure != null) {
        throw failure;
      }
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
}
