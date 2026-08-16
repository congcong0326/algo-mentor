package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

/** 仅在当前消息有明确代码级意图时，展开一次已签发正式提交的归一化代码。 */
public final class ReadPracticeSubmissionDetailAgentTool implements AgentTool {

  private static final Set<String> FIELDS = Set.of(PracticeSubmissionHistoryToolContracts.ARGUMENT_SUBMISSION_REF);
  private static final LlmToolSpec SPEC = new LlmToolSpec(
      PracticeSubmissionHistoryToolContracts.READ_PRACTICE_SUBMISSION_DETAIL,
      "Read one scoped historical submission's reviewed code only when the user explicitly asks for old-code review or comparison.",
      schema(),
      true);

  private final PracticeSubmissionHistoryRunScopeRegistry scopeRegistry;
  private final PracticeSubmissionHistoryToolRepository repository;
  private final TrustedProblemTagCatalog tagCatalog;
  private final ToolResultCompactionPolicy compactionPolicy;
  private final PracticeSubmissionHistoryToolMetrics metrics;

  public ReadPracticeSubmissionDetailAgentTool(
      PracticeSubmissionHistoryRunScopeRegistry scopeRegistry,
      PracticeSubmissionHistoryToolRepository repository,
      TrustedProblemTagCatalog tagCatalog,
      ToolResultCompactionPolicy compactionPolicy,
      PracticeSubmissionHistoryToolMetrics metrics
  ) {
    this.scopeRegistry = java.util.Objects.requireNonNull(scopeRegistry, "scopeRegistry must not be null");
    this.repository = java.util.Objects.requireNonNull(repository, "repository must not be null");
    this.tagCatalog = tagCatalog == null ? TrustedProblemTagCatalog.empty() : tagCatalog;
    this.compactionPolicy = compactionPolicy == null ? ToolResultCompactionPolicy.defaults() : compactionPolicy;
    this.metrics = metrics == null ? PracticeSubmissionHistoryToolMetrics.NOOP : metrics;
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    String submissionRef = PracticeSubmissionHistoryToolSupport.requiredText(
        arguments, PracticeSubmissionHistoryToolContracts.ARGUMENT_SUBMISSION_REF);
    if (!PracticeSubmissionHistoryToolSupport.hasOnlyFields(arguments, FIELDS) || submissionRef == null) {
      return record(PracticeSubmissionHistoryToolSupport.failure(
          PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_DETAIL,
          PracticeSubmissionHistoryToolContracts.STATUS_FAILED,
          PracticeSubmissionHistoryToolContracts.FAILURE_INVALID_ARGUMENTS));
    }
    if (!PracticeSubmissionHistoryToolSupport.codeDetailIntent(context)) {
      metrics.recordCodeIntentRejected();
      return record(PracticeSubmissionHistoryToolSupport.failure(
          PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_DETAIL,
          PracticeSubmissionHistoryToolContracts.STATUS_USER_INTENT_REQUIRED,
          PracticeSubmissionHistoryToolContracts.FAILURE_USER_INTENT_REQUIRED));
    }
    PracticeSubmissionHistoryRunScopeRegistry.ScopeUse use = scopeRegistry.reserveDetail(
        PracticeSubmissionHistoryToolSupport.scopeRef(context), submissionRef);
    if (!use.granted()) {
      metrics.recordScopeRejected(use.status().name());
      return record(PracticeSubmissionHistoryToolSupport.failureForScope(
          PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_DETAIL, use.status()));
    }
    try {
      return repository.findSubmissionDetail(use.userId(), use.submission().problemSlug(), use.submission().reviewId())
          .<JsonNode>map(detail -> render(use, detail))
          .orElseGet(() -> record(PracticeSubmissionHistoryToolSupport.failure(
              PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_DETAIL,
              PracticeSubmissionHistoryToolContracts.STATUS_UNAVAILABLE,
              PracticeSubmissionHistoryToolContracts.FAILURE_UNAVAILABLE)));
    } catch (RuntimeException ignored) {
      metrics.recordToolDataAccessFailure();
      return record(PracticeSubmissionHistoryToolSupport.failure(
          PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_DETAIL,
          PracticeSubmissionHistoryToolContracts.STATUS_FAILED,
          PracticeSubmissionHistoryToolContracts.FAILURE_DATA_ACCESS));
    }
  }

  private JsonNode render(
      PracticeSubmissionHistoryRunScopeRegistry.ScopeUse use,
      PracticeSubmissionHistoryDetail detail
  ) {
    if (detail.review().reviewId() != use.submission().reviewId()) {
      metrics.recordScopeRejected("UNAVAILABLE");
      return record(PracticeSubmissionHistoryToolSupport.failure(
          PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_DETAIL,
          PracticeSubmissionHistoryToolContracts.STATUS_UNAVAILABLE,
          PracticeSubmissionHistoryToolContracts.FAILURE_UNAVAILABLE));
    }
    ObjectNode result = PracticeSubmissionHistoryToolSupport.success(
        PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_DETAIL);
    ObjectNode submission = result.putObject(PracticeSubmissionHistoryToolContracts.FIELD_SUBMISSION);
    submission.put(PracticeSubmissionHistoryToolContracts.FIELD_SUBMISSION_REF,
        use.issueSubmissionRef(detail.review().reviewId()));
    submission.put(PracticeSubmissionHistoryToolContracts.FIELD_SUBMITTED_AT, detail.review().submittedAt().toString());
    submission.put(PracticeSubmissionHistoryToolContracts.FIELD_LANGUAGE, detail.review().language());
    submission.put(PracticeSubmissionHistoryToolContracts.FIELD_REVIEWED_CODE, detail.normalizedCode());
    ObjectNode review = submission.putObject(PracticeSubmissionHistoryToolContracts.FIELD_REVIEW);
    PracticeSubmissionHistoryToolSupport.writeSubmission(
        review,
        null,
        detail.review(),
        affectedTags(detail.review(), tagCatalog.findByProblemSlug(use.submission().problemSlug())),
        true);
    int visibleChars = PracticeSubmissionHistoryToolSupport.initialVisibleChars(
        result, compactionPolicy.inlineMaxChars(), compactionPolicy.previewMaxChars());
    if (!use.recordInitialDetailVisibleChars(visibleChars)) {
      return record(PracticeSubmissionHistoryToolSupport.failure(
          PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_DETAIL,
          PracticeSubmissionHistoryToolContracts.STATUS_BUDGET_EXHAUSTED,
          PracticeSubmissionHistoryToolContracts.FAILURE_BUDGET_EXHAUSTED));
    }
    metrics.recordDetailVisibleChars(visibleChars);
    return record(result);
  }

  private List<String> affectedTags(PracticeSubmissionHistoryReview review, List<TrustedProblemTag> catalogTags) {
    if (review.affectedTagIds().isEmpty()) {
      return List.of();
    }
    return catalogTags.stream()
        .filter(tag -> review.affectedTagIds().contains(tag.tagId()))
        .map(TrustedProblemTag::value)
        .toList();
  }

  private JsonNode record(JsonNode result) {
    metrics.recordToolCall(PracticeSubmissionHistoryToolContracts.READ_PRACTICE_SUBMISSION_DETAIL,
        result.path(PracticeSubmissionHistoryToolContracts.FIELD_STATUS).asText());
    return result;
  }

  private static ObjectNode schema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putObject("properties").putObject(PracticeSubmissionHistoryToolContracts.ARGUMENT_SUBMISSION_REF)
        .put("type", "string");
    schema.putArray("required").add(PracticeSubmissionHistoryToolContracts.ARGUMENT_SUBMISSION_REF);
    return schema;
  }
}
