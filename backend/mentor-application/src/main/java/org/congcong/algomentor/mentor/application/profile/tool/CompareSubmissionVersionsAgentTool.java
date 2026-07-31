package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewSubmissionVersion;
import org.congcong.algomentor.mentor.application.profile.review.history.ReviewTrajectory;
import org.congcong.algomentor.mentor.application.profile.review.history.ReviewTrajectoryService;
import org.congcong.algomentor.mentor.application.profile.review.history.ReviewTrajectoryVersion;
import org.congcong.algomentor.mentor.application.profile.review.history.SubmissionVersionDiff;
import org.congcong.algomentor.mentor.application.profile.review.history.SubmissionVersionDiffService;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** 比较当前 update scope 内同题且有序的两个正式提交版本。 */
public final class CompareSubmissionVersionsAgentTool implements AgentTool {

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      LearnerMemoryAgentToolContracts.COMPARE_SUBMISSION_VERSIONS,
      "Compare two ordered, scoped versions of the same formal submission and return only a bounded unified diff.",
      LearnerMemoryReviewToolSupport.twoIntegerArgumentsSchema(
          LearnerMemoryAgentToolContracts.ARGUMENT_FROM_REVIEW_ID,
          LearnerMemoryAgentToolContracts.ARGUMENT_TO_REVIEW_ID),
      true);

  private final LearnerMemoryRunScopeRegistry scopeRegistry;
  private final CodeReviewHistoryRepository historyRepository;
  private final SubmissionVersionDiffService diffService;
  private final ReviewTrajectoryService trajectoryService;
  private final LearnerMemoryReviewToolMetrics observability;

  public CompareSubmissionVersionsAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      CodeReviewHistoryRepository historyRepository,
      SubmissionVersionDiffService diffService,
      ReviewTrajectoryService trajectoryService
  ) {
    this(scopeRegistry, historyRepository, diffService, trajectoryService, LearnerMemoryMetrics.NOOP);
  }

  public CompareSubmissionVersionsAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      CodeReviewHistoryRepository historyRepository,
      SubmissionVersionDiffService diffService,
      ReviewTrajectoryService trajectoryService,
      LearnerMemoryMetrics metrics
  ) {
    this.scopeRegistry = Objects.requireNonNull(scopeRegistry, "scopeRegistry must not be null");
    this.historyRepository = Objects.requireNonNull(historyRepository, "historyRepository must not be null");
    this.diffService = Objects.requireNonNull(diffService, "diffService must not be null");
    this.trajectoryService = Objects.requireNonNull(trajectoryService, "trajectoryService must not be null");
    this.observability = new LearnerMemoryReviewToolMetrics(metrics);
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    long[] ids = LearnerMemoryReviewToolSupport.requiredOrderedPositiveLongArguments(
        arguments,
        LearnerMemoryAgentToolContracts.ARGUMENT_FROM_REVIEW_ID,
        LearnerMemoryAgentToolContracts.ARGUMENT_TO_REVIEW_ID);
    if (ids == null) {
      return observability.record(LearnerMemoryAgentToolContracts.COMPARE_SUBMISSION_VERSIONS,
          LearnerMemoryReviewToolSupport.failure(
          LearnerMemoryAgentToolContracts.RESULT_TYPE_SUBMISSION_DIFF,
          LearnerMemoryAgentToolContracts.STATUS_FAILED,
          LearnerMemoryAgentToolContracts.FAILURE_INVALID_ARGUMENTS));
    }
    LearnerMemoryRunScopeRegistry.ScopeUse use = scopeRegistry.reserveDiff(
        LearnerMemoryReviewToolSupport.scopeRef(context), ids[0], ids[1]);
    if (!use.granted()) {
      return observability.record(LearnerMemoryAgentToolContracts.COMPARE_SUBMISSION_VERSIONS,
          LearnerMemoryReviewToolSupport.failureForScopeUse(
              LearnerMemoryAgentToolContracts.RESULT_TYPE_SUBMISSION_DIFF, use.status()));
    }
    try {
      Map<Long, CodeReviewSubmissionVersion> versions = historyRepository.findNormalizedSubmissionVersions(
          use.scope().userId(), List.of(ids[0], ids[1])).stream()
          .collect(Collectors.toMap(CodeReviewSubmissionVersion::reviewId, version -> version));
      CodeReviewSubmissionVersion from = versions.get(ids[0]);
      CodeReviewSubmissionVersion to = versions.get(ids[1]);
      if (!matchesScope(use.scope().reviewsById(), from) || !matchesScope(use.scope().reviewsById(), to)) {
        return observability.record(LearnerMemoryAgentToolContracts.COMPARE_SUBMISSION_VERSIONS,
            LearnerMemoryReviewToolSupport.failure(
            LearnerMemoryAgentToolContracts.RESULT_TYPE_SUBMISSION_DIFF,
            LearnerMemoryAgentToolContracts.STATUS_FAILED,
            LearnerMemoryAgentToolContracts.FAILURE_REVIEW_NOT_FOUND));
      }
      ReviewTrajectoryVersion changes = comparison(use.scope().userId(), from, to);
      if (changes == null) {
        return observability.record(LearnerMemoryAgentToolContracts.COMPARE_SUBMISSION_VERSIONS,
            LearnerMemoryReviewToolSupport.failure(
            LearnerMemoryAgentToolContracts.RESULT_TYPE_SUBMISSION_DIFF,
            LearnerMemoryAgentToolContracts.STATUS_FAILED,
            LearnerMemoryAgentToolContracts.FAILURE_REVIEW_DATA_UNAVAILABLE));
      }
      SubmissionVersionDiff diff = diffService.diff(from, to);
      return observability.record(LearnerMemoryAgentToolContracts.COMPARE_SUBMISSION_VERSIONS,
          render(from, to, changes, diff));
    } catch (RuntimeException exception) {
      return observability.record(LearnerMemoryAgentToolContracts.COMPARE_SUBMISSION_VERSIONS,
          LearnerMemoryReviewToolSupport.failure(
          LearnerMemoryAgentToolContracts.RESULT_TYPE_SUBMISSION_DIFF,
          LearnerMemoryAgentToolContracts.STATUS_FAILED,
          LearnerMemoryAgentToolContracts.FAILURE_INTERNAL));
    }
  }

  private boolean matchesScope(
      Map<Long, org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification> reviews,
      CodeReviewSubmissionVersion version
  ) {
    if (version == null) {
      return false;
    }
    org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification expected =
        reviews.get(version.reviewId());
    return expected != null && expected.problemSlug().equals(version.problemSlug())
        && expected.versionNo() == version.versionNo();
  }

  private JsonNode render(
      CodeReviewSubmissionVersion from,
      CodeReviewSubmissionVersion to,
      ReviewTrajectoryVersion changes,
      SubmissionVersionDiff diff
  ) {
    ObjectNode result = LearnerMemoryReviewToolSupport.success(
        LearnerMemoryAgentToolContracts.RESULT_TYPE_SUBMISSION_DIFF);
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_FROM_REVIEW_ID, from.reviewId());
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_TO_REVIEW_ID, to.reviewId());
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_PROBLEM_SLUG, from.problemSlug());
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_SCORE_DELTA, changes.scoreDelta());
    LearnerMemoryReviewToolSupport.writeStrings(
        result.putArray(LearnerMemoryAgentToolContracts.RESULT_FIELD_PERSISTED_FINDINGS), changes.persistedFindings());
    LearnerMemoryReviewToolSupport.writeStrings(
        result.putArray(LearnerMemoryAgentToolContracts.RESULT_FIELD_RESOLVED_FINDINGS), changes.resolvedFindings());
    LearnerMemoryReviewToolSupport.writeStrings(
        result.putArray(LearnerMemoryAgentToolContracts.RESULT_FIELD_NEW_FINDINGS), changes.newFindings());
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_UNIFIED_DIFF, diff.unifiedDiff());
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_TRUNCATED, diff.truncated());
    return result;
  }

  private ReviewTrajectoryVersion comparison(
      long userId,
      CodeReviewSubmissionVersion from,
      CodeReviewSubmissionVersion to
  ) {
    Map<Long, org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistory> byId =
        historyRepository.findLatestForProblem(userId, from.problemSlug(), ReviewTrajectoryService.MAX_VERSIONS).stream()
            .collect(Collectors.toMap(
                org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistory::reviewId,
                review -> review));
    org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistory fromReview = byId.get(from.reviewId());
    org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistory toReview = byId.get(to.reviewId());
    if (fromReview == null || toReview == null) {
      return null;
    }
    ReviewTrajectory trajectory = trajectoryService.calculate(List.of(fromReview, toReview));
    return trajectory.versions().get(1);
  }
}
