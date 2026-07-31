package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistory;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.profile.review.history.ReviewTrajectory;
import org.congcong.algomentor.mentor.application.profile.review.history.ReviewTrajectoryService;
import org.congcong.algomentor.mentor.application.profile.review.history.ReviewTrajectoryVersion;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** 读取当前 update scope 内单题最近五个正式 Review 的纵向轨迹。 */
public final class GetProblemReviewTrajectoryAgentTool implements AgentTool {

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
      "Get the scoped problem's recent formal Review trajectory without retrieving source code or review Markdown.",
      LearnerMemoryReviewToolSupport.inputSchema(
          LearnerMemoryAgentToolContracts.ARGUMENT_PROBLEM_SLUG, "string"),
      true);

  private final LearnerMemoryRunScopeRegistry scopeRegistry;
  private final CodeReviewHistoryRepository historyRepository;
  private final ReviewTrajectoryService trajectoryService;
  private final LearnerMemoryReviewToolMetrics observability;

  public GetProblemReviewTrajectoryAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      CodeReviewHistoryRepository historyRepository,
      ReviewTrajectoryService trajectoryService
  ) {
    this(scopeRegistry, historyRepository, trajectoryService, LearnerMemoryMetrics.NOOP);
  }

  public GetProblemReviewTrajectoryAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      CodeReviewHistoryRepository historyRepository,
      ReviewTrajectoryService trajectoryService,
      LearnerMemoryMetrics metrics
  ) {
    this.scopeRegistry = Objects.requireNonNull(scopeRegistry, "scopeRegistry must not be null");
    this.historyRepository = Objects.requireNonNull(historyRepository, "historyRepository must not be null");
    this.trajectoryService = Objects.requireNonNull(trajectoryService, "trajectoryService must not be null");
    this.observability = new LearnerMemoryReviewToolMetrics(metrics);
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    String problemSlug = LearnerMemoryReviewToolSupport.requiredStringArgument(
        arguments, LearnerMemoryAgentToolContracts.ARGUMENT_PROBLEM_SLUG);
    if (problemSlug == null) {
      return observability.record(LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
          LearnerMemoryReviewToolSupport.failure(
          LearnerMemoryAgentToolContracts.RESULT_TYPE_REVIEW_TRAJECTORY,
          LearnerMemoryAgentToolContracts.STATUS_FAILED,
          LearnerMemoryAgentToolContracts.FAILURE_INVALID_ARGUMENTS));
    }
    LearnerMemoryRunScopeRegistry.ScopeUse use = scopeRegistry.reserveTrajectory(
        LearnerMemoryReviewToolSupport.scopeRef(context), problemSlug);
    if (!use.granted()) {
      return observability.record(LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
          LearnerMemoryReviewToolSupport.failureForScopeUse(
              LearnerMemoryAgentToolContracts.RESULT_TYPE_REVIEW_TRAJECTORY, use.status()));
    }
    String purpose = use.scope().purpose().metricValue();
    try {
      List<CodeReviewHistory> reviews = historyRepository.findLatestForProblem(
          use.scope().userId(), problemSlug, ReviewTrajectoryService.MAX_VERSIONS);
      if (reviews.isEmpty()) {
        return observability.record(purpose, LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
            LearnerMemoryReviewToolSupport.failure(
            LearnerMemoryAgentToolContracts.RESULT_TYPE_REVIEW_TRAJECTORY,
            LearnerMemoryAgentToolContracts.STATUS_FAILED,
            LearnerMemoryAgentToolContracts.FAILURE_REVIEW_NOT_FOUND));
      }
      return observability.record(purpose, LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
          render(trajectoryService.calculate(reviews)));
    } catch (RuntimeException exception) {
      return observability.record(purpose, LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
          LearnerMemoryReviewToolSupport.failure(
          LearnerMemoryAgentToolContracts.RESULT_TYPE_REVIEW_TRAJECTORY,
          LearnerMemoryAgentToolContracts.STATUS_FAILED,
          LearnerMemoryAgentToolContracts.FAILURE_INTERNAL));
    }
  }

  private JsonNode render(ReviewTrajectory trajectory) {
    ObjectNode result = LearnerMemoryReviewToolSupport.success(
        LearnerMemoryAgentToolContracts.RESULT_TYPE_REVIEW_TRAJECTORY);
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_PROBLEM_SLUG, trajectory.problemSlug());
    ArrayNode versions = result.putArray(LearnerMemoryAgentToolContracts.RESULT_FIELD_VERSIONS);
    for (ReviewTrajectoryVersion version : trajectory.versions()) {
      ObjectNode node = versions.addObject();
      LearnerMemoryReviewToolSupport.writeReview(node, version.review());
      if (version.scoreDelta() == null) {
        node.putNull(LearnerMemoryAgentToolContracts.RESULT_FIELD_SCORE_DELTA);
      } else {
        node.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_SCORE_DELTA, version.scoreDelta());
      }
      LearnerMemoryReviewToolSupport.writeStrings(
          node.putArray(LearnerMemoryAgentToolContracts.RESULT_FIELD_PERSISTED_FINDINGS), version.persistedFindings());
      LearnerMemoryReviewToolSupport.writeStrings(
          node.putArray(LearnerMemoryAgentToolContracts.RESULT_FIELD_RESOLVED_FINDINGS), version.resolvedFindings());
      LearnerMemoryReviewToolSupport.writeStrings(
          node.putArray(LearnerMemoryAgentToolContracts.RESULT_FIELD_NEW_FINDINGS), version.newFindings());
    }
    return LearnerMemoryReviewToolSupport.limitResult(result);
  }
}
