package org.congcong.algomentor.mentor.application.profile.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewEvidenceDetail;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistory;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewSubmissionVersion;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;
import org.congcong.algomentor.mentor.application.profile.review.history.ReviewTrajectoryService;
import org.congcong.algomentor.mentor.application.profile.review.history.SubmissionVersionDiffService;
import org.congcong.algomentor.mentor.application.profile.observability.MicrometerLearnerMemoryMetrics;
import org.junit.jupiter.api.Test;

class LearnerMemoryReviewToolTest {

  @Test
  void enforcesScopeLimitsBeforeRepositoryReadsAndBoundsEvidenceOutput() {
    FakeHistoryRepository repository = new FakeHistoryRepository();
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    MicrometerLearnerMemoryMetrics metrics = new MicrometerLearnerMemoryMetrics(meterRegistry);
    LearnerMemoryRunScopeRegistry.ScopeLease lease = registry.openUpdateScope(7, List.of(
        verification(101, "two-sum", 1), verification(102, "two-sum", 2)));
    AgentExecutionContext context = context(registry, lease);
    GetProblemReviewTrajectoryAgentTool trajectoryTool = new GetProblemReviewTrajectoryAgentTool(
        registry, repository, new ReviewTrajectoryService(), metrics);
    GetCodeReviewEvidenceAgentTool evidenceTool = new GetCodeReviewEvidenceAgentTool(registry, repository, metrics);
    CompareSubmissionVersionsAgentTool diffTool = new CompareSubmissionVersionsAgentTool(
        registry, repository, new SubmissionVersionDiffService(), new ReviewTrajectoryService(), metrics);

    JsonNode denied = evidenceTool.execute(object(LearnerMemoryAgentToolContracts.ARGUMENT_REVIEW_ID, 999), context);
    assertThat(denied.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_FAILURE_CODE).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.FAILURE_SCOPE_FORBIDDEN);
    assertThat(repository.evidenceCalls).isZero();

    JsonNode first = trajectoryTool.execute(object(LearnerMemoryAgentToolContracts.ARGUMENT_PROBLEM_SLUG, "two-sum"), context);
    assertThat(first.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_STATUS).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.STATUS_OK);
    assertThat(first.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_VERSIONS)).hasSize(2);

    JsonNode evidence = evidenceTool.execute(object(LearnerMemoryAgentToolContracts.ARGUMENT_REVIEW_ID, 101), context);
    assertThat(evidence.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_STATUS).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.STATUS_OK);
    assertThat(evidence.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_TRUNCATED).asBoolean()).isTrue();
    assertThat(evidence.toString().length()).isLessThanOrEqualTo(LearnerMemoryAgentToolContracts.MAX_TOOL_RESULT_CHARS);
    assertThat(evidence.toString()).doesNotContain("rawCode", "normalizedCode", "reviewMarkdown");

    JsonNode diff = diffTool.execute(object(
        LearnerMemoryAgentToolContracts.ARGUMENT_FROM_REVIEW_ID, 101,
        LearnerMemoryAgentToolContracts.ARGUMENT_TO_REVIEW_ID, 102), context);
    assertThat(diff.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_STATUS).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.STATUS_OK);
    assertThat(diff.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_UNIFIED_DIFF).asText())
        .startsWith("--- submission-v1-101\n+++ submission-v2-102");

    JsonNode overBudget = evidenceTool.execute(object(LearnerMemoryAgentToolContracts.ARGUMENT_REVIEW_ID, 102), context);
    assertThat(overBudget.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_STATUS).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.STATUS_BUDGET_EXHAUSTED);
    assertThat(overBudget.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_FAILURE_CODE).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.FAILURE_BUDGET_EXHAUSTED);
    assertThat(repository.evidenceCalls).isEqualTo(1);
    assertThat(meterRegistry.get("learner_memory_tool_call_total")
        .tags("purpose", "REVIEW_UPDATE", "tool", LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
            "status", "SUCCEEDED").counter().count()).isEqualTo(1D);
    assertThat(meterRegistry.get("learner_memory_tool_call_total")
        .tags("purpose", "REVIEW_UPDATE", "tool", LearnerMemoryAgentToolContracts.GET_CODE_REVIEW_EVIDENCE,
            "status", "SUCCEEDED").counter().count()).isEqualTo(1D);
    assertThat(meterRegistry.get("learner_memory_tool_call_total")
        .tags("purpose", "REVIEW_UPDATE", "tool", LearnerMemoryAgentToolContracts.COMPARE_SUBMISSION_VERSIONS,
            "status", "SUCCEEDED").counter().count()).isEqualTo(1D);
  }

  @Test
  void rejectsMissingReleasedAndCrossProblemScopesWithoutFallbackQuery() {
    FakeHistoryRepository repository = new FakeHistoryRepository();
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    GetCodeReviewEvidenceAgentTool evidenceTool = new GetCodeReviewEvidenceAgentTool(registry, repository);

    JsonNode missing = evidenceTool.execute(object(LearnerMemoryAgentToolContracts.ARGUMENT_REVIEW_ID, 101),
        new AgentExecutionContext("run", 1, java.util.Map.of(), false));
    assertThat(missing.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_FAILURE_CODE).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.FAILURE_SCOPE_UNAVAILABLE);

    LearnerMemoryRunScopeRegistry.ScopeLease lease = registry.openUpdateScope(7, List.of(
        verification(101, "two-sum", 1), verification(201, "other", 2)));
    CompareSubmissionVersionsAgentTool diffTool = new CompareSubmissionVersionsAgentTool(
        registry, repository, new SubmissionVersionDiffService(), new ReviewTrajectoryService());
    JsonNode crossProblem = diffTool.execute(object(
        LearnerMemoryAgentToolContracts.ARGUMENT_FROM_REVIEW_ID, 101,
        LearnerMemoryAgentToolContracts.ARGUMENT_TO_REVIEW_ID, 201), context(registry, lease));
    assertThat(crossProblem.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_FAILURE_CODE).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.FAILURE_SCOPE_FORBIDDEN);
    lease.release();

    JsonNode released = evidenceTool.execute(object(LearnerMemoryAgentToolContracts.ARGUMENT_REVIEW_ID, 101),
        context(registry, lease));
    assertThat(released.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_FAILURE_CODE).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.FAILURE_SCOPE_UNAVAILABLE);
    assertThat(repository.evidenceCalls).isZero();
    assertThat(repository.versionCalls).isZero();
  }

  @Test
  void readsPracticeChatTrajectoryOnlyForTheTrustedCurrentProblemAndUser() {
    FakeHistoryRepository repository = new FakeHistoryRepository();
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryRunScopeRegistry.ScopeLease lease = registry.openPracticeChatTrajectoryScope(7, "two-sum");
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    GetProblemReviewTrajectoryAgentTool tool = new GetProblemReviewTrajectoryAgentTool(
        registry, repository, new ReviewTrajectoryService(), new MicrometerLearnerMemoryMetrics(meterRegistry));
    AgentExecutionContext context = context(registry, lease);

    JsonNode forbidden = tool.execute(object(LearnerMemoryAgentToolContracts.ARGUMENT_PROBLEM_SLUG, "other"), context);
    assertThat(forbidden.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_FAILURE_CODE).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.FAILURE_SCOPE_FORBIDDEN);
    assertThat(repository.trajectoryCalls).isZero();

    JsonNode result = tool.execute(object(LearnerMemoryAgentToolContracts.ARGUMENT_PROBLEM_SLUG, "two-sum"), context);
    assertThat(result.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_STATUS).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.STATUS_OK);
    assertThat(repository.lastTrajectoryUserId).isEqualTo(7L);
    assertThat(repository.trajectoryCalls).isEqualTo(1);

    JsonNode repeated = tool.execute(object(LearnerMemoryAgentToolContracts.ARGUMENT_PROBLEM_SLUG, "two-sum"), context);
    assertThat(repeated.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_FAILURE_CODE).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.FAILURE_TOOL_ALREADY_USED);
    assertThat(repository.trajectoryCalls).isEqualTo(1);
    assertThat(meterRegistry.get("learner_memory_tool_call_total")
        .tags("purpose", "PRACTICE_CHAT", "tool", LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
            "status", "SUCCEEDED").counter().count()).isEqualTo(1D);
  }

  private static AgentExecutionContext context(
      LearnerMemoryRunScopeRegistry registry,
      LearnerMemoryRunScopeRegistry.ScopeLease lease
  ) {
    return new AgentExecutionContext("run", 1, registry.initialRequestMetadata(lease), false);
  }

  private static JsonNode object(String field, long value) {
    return JsonNodeFactory.instance.objectNode().put(field, value);
  }

  private static JsonNode object(String field, String value) {
    return JsonNodeFactory.instance.objectNode().put(field, value);
  }

  private static JsonNode object(String firstField, long firstValue, String secondField, long secondValue) {
    return JsonNodeFactory.instance.objectNode().put(firstField, firstValue).put(secondField, secondValue);
  }

  private static CodeReviewVerification verification(long id, String slug, int version) {
    return new CodeReviewVerification(id, slug, version, List.of(8L), Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static CodeReviewHistory review(long id, int version, List<String> findings) {
    return new CodeReviewHistory(
        id, "two-sum", version,
        new PracticeCodeReviewScore(new BigDecimal("3"), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
            BigDecimal.ONE, new BigDecimal("7")),
        false, findings, List.of("extract a helper"), List.of(8L),
        Instant.parse("2026-01-0" + version + "T00:00:00Z"));
  }

  private static final class FakeHistoryRepository implements CodeReviewHistoryRepository {

    private final List<CodeReviewHistory> history = List.of(
        review(101, 1, List.of("nested loop")), review(102, 2, List.of("null case")));
    private int evidenceCalls;
    private int versionCalls;
    private int trajectoryCalls;
    private long lastTrajectoryUserId;

    @Override
    public List<CodeReviewHistory> findLatestForProblem(long userId, String problemSlug, int limit) {
      trajectoryCalls++;
      lastTrajectoryUserId = userId;
      return "two-sum".equals(problemSlug) ? history : List.of();
    }

    @Override
    public Optional<CodeReviewEvidenceDetail> findEvidenceDetail(long userId, long reviewId) {
      evidenceCalls++;
      return history.stream().filter(review -> review.reviewId() == reviewId).findFirst().map(review ->
          new CodeReviewEvidenceDetail(
              review,
              List.of(new PracticeCodeReviewEvidence("complexity", "e".repeat(4_000))),
              "context ".repeat(900)));
    }

    @Override
    public List<CodeReviewSubmissionVersion> findNormalizedSubmissionVersions(long userId, List<Long> reviewIds) {
      versionCalls++;
      return List.of(
          new CodeReviewSubmissionVersion(101, "two-sum", 1, "return first;"),
          new CodeReviewSubmissionVersion(102, "two-sum", 2, "return second;"));
    }

    @Override
    public List<CodeReviewVerification> verifyReviews(long userId, List<Long> reviewIds) {
      return List.of();
    }
  }
}
