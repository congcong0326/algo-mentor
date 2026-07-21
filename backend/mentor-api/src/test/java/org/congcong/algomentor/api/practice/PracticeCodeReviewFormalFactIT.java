package org.congcong.algomentor.api.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeCodeReviewRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewPromptBuilder;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewStructuredOutputMapper;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewSummary;
import org.congcong.algomentor.mentor.application.practice.PracticeReviewResult;
import org.congcong.algomentor.mentor.application.practice.PracticeReviewStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeTurnContext;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.publisher.QueuePublisher;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewFormalFactIT extends PostgresIntegrationTestSupport {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void nonReviewAndFailedPathsLeaveNoRowsWhileFormalReviewRemainsIdempotent() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long sessionId = insertPracticeSession(userId, "two-sum");
    long messageId = insertUserMessage(userId);
    PracticeCodeReviewRepository repository = transactionalRepository(repository());
    PracticeTurnContext context = new PracticeTurnContext(
        userId, 1, 1, "two-sum", sessionId, messageId, null, null,
        "", "", "class Solution {}", "class Solution {}", "", "zh-CN");

    PracticeReviewResult nonReview = service(repository, structuredOutput(false), null).review(context);
    PracticeReviewResult failed = service(repository, null, new IllegalStateException("provider unavailable")).review(context);

    assertThat(nonReview.status()).isEqualTo(PracticeReviewStatus.NOT_CODE_LIKE);
    assertThat(failed.status()).isEqualTo(PracticeReviewStatus.FAILED);
    assertThat(count("practice_code_review")).isZero();

    PracticeCodeReviewService successfulService = service(repository, structuredOutput(true), null);
    assertThat(successfulService.review(context).status()).isEqualTo(PracticeReviewStatus.SAVED);
    assertThat(successfulService.review(context).status()).isEqualTo(PracticeReviewStatus.SAVED);
    assertThat(count("practice_code_review")).isEqualTo(1L);
  }

  private PracticeCodeReviewService service(PracticeCodeReviewRepository repository, JsonNode output, RuntimeException failure) {
    return new PracticeCodeReviewService(
        repository,
        new PracticeCodeReviewCommitService(repository, queuePublisher()),
        new FixedGateway(output, failure),
        new PracticeCodeReviewPromptBuilder(),
        new PracticeCodeReviewStructuredOutputMapper());
  }

  private QueuePublisher queuePublisher() {
    return (topic, key, payload) -> new QueueMessage(1L, topic, key, "{}", java.time.Instant.EPOCH);
  }

  private MyBatisPracticeCodeReviewRepository repository() throws Exception {
    return new MyBatisPracticeCodeReviewRepository(
        sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
        objectMapper);
  }

  private PracticeCodeReviewRepository transactionalRepository(MyBatisPracticeCodeReviewRepository delegate) {
    return new PracticeCodeReviewRepository() {
      @Override
      public PracticeCodeReview save(PracticeCodeReviewDraft draft) {
        return transactionTemplate().execute(status -> delegate.save(draft));
      }

      @Override
      public org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewSaveResult saveResult(
          PracticeCodeReviewDraft draft) {
        return transactionTemplate().execute(status -> delegate.saveResult(draft));
      }

      @Override
      public Optional<PracticeCodeReviewSummary> findLatestSummary(long userId, long sessionId) {
        return delegate.findLatestSummary(userId, sessionId);
      }

      @Override
      public Optional<PracticeCodeReview> findLatest(long userId, long sessionId) {
        return delegate.findLatest(userId, sessionId);
      }

      @Override
      public List<PracticeCodeReviewSummary> findSummaries(long userId, long sessionId) {
        return delegate.findSummaries(userId, sessionId);
      }

      @Override
      public Optional<PracticeCodeReview> findById(long userId, long sessionId, long reviewId) {
        return delegate.findById(userId, sessionId, reviewId);
      }

      @Override
      public Optional<PracticeCodeReview> findByUserMessage(long userId, long sessionId, long userMessageId) {
        return delegate.findByUserMessage(userId, sessionId, userMessageId);
      }
    };
  }

  private JsonNode structuredOutput(boolean isCodeSubmission) {
    return objectMapper.valueToTree(Map.ofEntries(
        Map.entry("isCodeSubmission", isCodeSubmission),
        Map.entry("belongsToCurrentProblem", true),
        Map.entry("isCompleteLeetCodeSolution", true),
        Map.entry("language", "java"),
        Map.entry("rawCode", "class Solution {}"),
        Map.entry("normalizedCode", "class Solution {}"),
        Map.entry("evidence", List.of()),
        Map.entry("contextSummary", ""),
        Map.entry("scores", Map.of(
            "correctness", new BigDecimal("4"), "complexity", new BigDecimal("2"),
            "edgeCases", new BigDecimal("2"), "codeQuality", BigDecimal.ONE,
            "problemFit", BigDecimal.ONE, "total", new BigDecimal("10"))),
        Map.entry("passed", true),
        Map.entry("deductionReasons", List.of()),
        Map.entry("improvementSuggestions", List.of()),
        Map.entry("reviewMarkdown", "OK"),
        Map.entry("affectedTagIds", List.of())));
  }

  private static final class FixedGateway implements LlmGateway {
    private final JsonNode output;
    private final RuntimeException failure;

    private FixedGateway(JsonNode output, RuntimeException failure) {
      this.output = output;
      this.failure = failure;
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      if (failure != null) {
        throw failure;
      }
      return new LlmCompletionResult(
          LlmMessage.assistant("{}"), List.of(), output, LlmFinishReason.STOP, null,
          new LlmProviderId("test"), new LlmModelId("test"), Map.of());
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }
  }
}
