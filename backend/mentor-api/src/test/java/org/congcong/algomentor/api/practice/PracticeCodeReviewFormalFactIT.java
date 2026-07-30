package org.congcong.algomentor.api.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeCodeReviewRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentOutput;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewConstants;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
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
    long parentRunId = insertParentRun(userId);
    PracticeCodeReviewRepository repository = transactionalRepository(repository());
    PracticeTurnContext context = new PracticeTurnContext(
        userId, 1, 1, "two-sum", sessionId, messageId, null, parentRunId,
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
        new FixedRuntime(output, failure),
        new PracticeCodeReviewStructuredOutputMapper(),
        org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetrics.NOOP,
        org.congcong.algomentor.mentor.application.review.card.PracticeCodeReviewObserver.NOOP);
  }

  private QueuePublisher queuePublisher() {
    return (topic, key, payload) -> new QueueMessage(1L, topic, key, "{}", java.time.Instant.EPOCH);
  }

  private long insertParentRun(long userId) throws Exception {
    String unique = Long.toUnsignedString(System.nanoTime(), 36);
    return queryLong("""
        WITH created_task AS (
          INSERT INTO agent_task (user_id, status, context_policy, metadata, created_at, updated_at)
          VALUES (?, 'COMPLETED', '{}'::jsonb, '{}'::jsonb, NOW(), NOW())
          RETURNING id
        ), created_turn AS (
          INSERT INTO agent_turn (task_id, sequence_no, status, created_at, updated_at)
          SELECT id, 1, 'COMPLETED', NOW(), NOW()
          FROM created_task
          RETURNING id, task_id
        )
        INSERT INTO agent_run (
          task_id, turn_id, run_uuid, attempt_no, idempotency_key, trigger_type, status, max_steps,
          usage, error, started_at, ended_at
        )
        SELECT task_id, id, ?, 1, ?, 'USER_ENTRY', 'COMPLETED', 1,
          '{}'::jsonb, '{}'::jsonb, NOW(), NOW()
        FROM created_turn
        RETURNING id
        """, userId, "parent-run-" + unique, "parent-idempotency-" + unique);
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
        Map.entry(PracticeCodeReviewConstants.JSON_JUDGE_ASSESSMENT, Map.of(
            PracticeCodeReviewConstants.JSON_JUDGE_VERDICT, "LIKELY_ACCEPTED",
            PracticeCodeReviewConstants.JSON_VERDICT_BASIS, "STATIC_ANALYSIS",
            PracticeCodeReviewConstants.JSON_BLOCKING_ISSUE, false,
            PracticeCodeReviewConstants.JSON_MEETS_EXPECTED_COMPLEXITY, true,
            PracticeCodeReviewConstants.JSON_TIME_COMPLEXITY, "O(1)",
            PracticeCodeReviewConstants.JSON_SPACE_COMPLEXITY, "O(1)",
            PracticeCodeReviewConstants.JSON_EXPECTED_TIME_COMPLEXITY, "O(1)",
            PracticeCodeReviewConstants.JSON_CONSTRAINT_ANALYSIS, "最大约束下预计可以通过。")),
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

  private static final class FixedRuntime implements AgentRuntime {
    private final JsonNode output;
    private final RuntimeException failure;

    private FixedRuntime(JsonNode output, RuntimeException failure) {
      this.output = output;
      this.failure = failure;
    }

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      if (failure != null) {
        throw failure;
      }
      return new AgentRunResult(
          1,
          LlmFinishReason.STOP,
          new AgentOutput("{}", output, PracticeCodeReviewConstants.SCHEMA_NAME,
              PracticeCodeReviewConstants.SCHEMA_VERSION, Map.of()),
          Map.of());
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException();
    }
  }
}
