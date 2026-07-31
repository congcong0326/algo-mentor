package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewEvidence;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewEvidenceDetail;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** 读取当前 update scope 内某条正式 Review 的受限证据详情。 */
public final class GetCodeReviewEvidenceAgentTool implements AgentTool {

  private static final LlmToolSpec SPEC = new LlmToolSpec(
      LearnerMemoryAgentToolContracts.GET_CODE_REVIEW_EVIDENCE,
      "Get constrained evidence for one scoped formal Review. Source code and review Markdown are never returned.",
      LearnerMemoryReviewToolSupport.inputSchema(
          LearnerMemoryAgentToolContracts.ARGUMENT_REVIEW_ID, "integer"),
      true);

  private final LearnerMemoryRunScopeRegistry scopeRegistry;
  private final CodeReviewHistoryRepository historyRepository;
  private final LearnerMemoryReviewToolMetrics observability;

  public GetCodeReviewEvidenceAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      CodeReviewHistoryRepository historyRepository
  ) {
    this(scopeRegistry, historyRepository, LearnerMemoryMetrics.NOOP);
  }

  public GetCodeReviewEvidenceAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      CodeReviewHistoryRepository historyRepository,
      LearnerMemoryMetrics metrics
  ) {
    this.scopeRegistry = Objects.requireNonNull(scopeRegistry, "scopeRegistry must not be null");
    this.historyRepository = Objects.requireNonNull(historyRepository, "historyRepository must not be null");
    this.observability = new LearnerMemoryReviewToolMetrics(metrics);
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    Long reviewId = LearnerMemoryReviewToolSupport.requiredPositiveLongArgument(
        arguments, LearnerMemoryAgentToolContracts.ARGUMENT_REVIEW_ID);
    if (reviewId == null) {
      return observability.record(LearnerMemoryAgentToolContracts.GET_CODE_REVIEW_EVIDENCE,
          LearnerMemoryReviewToolSupport.failure(
          LearnerMemoryAgentToolContracts.RESULT_TYPE_REVIEW_EVIDENCE,
          LearnerMemoryAgentToolContracts.STATUS_FAILED,
          LearnerMemoryAgentToolContracts.FAILURE_INVALID_ARGUMENTS));
    }
    LearnerMemoryRunScopeRegistry.ScopeUse use = scopeRegistry.reserveEvidence(
        LearnerMemoryReviewToolSupport.scopeRef(context), reviewId);
    if (!use.granted()) {
      return observability.record(LearnerMemoryAgentToolContracts.GET_CODE_REVIEW_EVIDENCE,
          LearnerMemoryReviewToolSupport.failureForScopeUse(
              LearnerMemoryAgentToolContracts.RESULT_TYPE_REVIEW_EVIDENCE, use.status()));
    }
    try {
      JsonNode result = historyRepository.findEvidenceDetail(use.scope().userId(), reviewId)
          .filter(detail -> matchesScope(use.scope().reviewsById().get(reviewId), detail))
          .map(this::render)
          .orElseGet(() -> LearnerMemoryReviewToolSupport.failure(
              LearnerMemoryAgentToolContracts.RESULT_TYPE_REVIEW_EVIDENCE,
              LearnerMemoryAgentToolContracts.STATUS_FAILED,
              LearnerMemoryAgentToolContracts.FAILURE_REVIEW_NOT_FOUND));
      return observability.record(LearnerMemoryAgentToolContracts.GET_CODE_REVIEW_EVIDENCE, result);
    } catch (RuntimeException exception) {
      return observability.record(LearnerMemoryAgentToolContracts.GET_CODE_REVIEW_EVIDENCE,
          LearnerMemoryReviewToolSupport.failure(
          LearnerMemoryAgentToolContracts.RESULT_TYPE_REVIEW_EVIDENCE,
          LearnerMemoryAgentToolContracts.STATUS_FAILED,
          LearnerMemoryAgentToolContracts.FAILURE_INTERNAL));
    }
  }

  private JsonNode render(CodeReviewEvidenceDetail detail) {
    ObjectNode result = LearnerMemoryReviewToolSupport.success(
        LearnerMemoryAgentToolContracts.RESULT_TYPE_REVIEW_EVIDENCE);
    LearnerMemoryReviewToolSupport.writeReview(
        result.putObject(LearnerMemoryAgentToolContracts.RESULT_FIELD_REVIEW), detail.review());
    result.put(LearnerMemoryAgentToolContracts.RESULT_FIELD_CONTEXT_SUMMARY, detail.contextSummary());
    ArrayNode evidence = result.putArray(LearnerMemoryAgentToolContracts.RESULT_FIELD_DETECTION_EVIDENCE);
    for (PracticeCodeReviewEvidence item : detail.detectionEvidence()) {
      evidence.addObject()
          .put(LearnerMemoryAgentToolContracts.RESULT_FIELD_EVIDENCE_TYPE, item.type())
          .put(LearnerMemoryAgentToolContracts.RESULT_FIELD_EVIDENCE_VALUE, item.value());
    }
    return LearnerMemoryReviewToolSupport.limitResult(result);
  }

  private boolean matchesScope(
      org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification expected,
      CodeReviewEvidenceDetail detail
  ) {
    return expected != null && expected.reviewId() == detail.reviewId()
        && expected.problemSlug().equals(detail.problemSlug()) && expected.versionNo() == detail.versionNo();
  }
}
