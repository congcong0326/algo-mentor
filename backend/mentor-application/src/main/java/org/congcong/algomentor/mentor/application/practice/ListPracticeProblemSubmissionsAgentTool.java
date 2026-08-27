package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

/** 按 keyset 分页读取当前 run 已授权历史题的正式 Review 时间线，不读取源码。 */
public final class ListPracticeProblemSubmissionsAgentTool implements AgentTool {

  private static final Set<String> FIELDS = Set.of(
      PracticeSubmissionHistoryToolContracts.ARGUMENT_PROBLEM_REF,
      PracticeSubmissionHistoryToolContracts.ARGUMENT_CURSOR,
      PracticeSubmissionHistoryToolContracts.ARGUMENT_LIMIT);
  private static final LlmToolSpec SPEC = new LlmToolSpec(
      PracticeSubmissionHistoryToolContracts.LIST_PRACTICE_PROBLEM_SUBMISSIONS,
      "List a scoped problem's formal submission timeline. This never returns source code or review Markdown.",
      schema(),
      true);

  private final PracticeSubmissionHistoryRunScopeRegistry scopeRegistry;
  private final PracticeSubmissionHistoryToolRepository repository;
  private final TrustedProblemTagCatalog tagCatalog;
  private final PracticeSubmissionHistoryToolMetrics metrics;

  public ListPracticeProblemSubmissionsAgentTool(
      PracticeSubmissionHistoryRunScopeRegistry scopeRegistry,
      PracticeSubmissionHistoryToolRepository repository,
      TrustedProblemTagCatalog tagCatalog,
      PracticeSubmissionHistoryToolMetrics metrics
  ) {
    this.scopeRegistry = java.util.Objects.requireNonNull(scopeRegistry, "scopeRegistry must not be null");
    this.repository = java.util.Objects.requireNonNull(repository, "repository must not be null");
    this.tagCatalog = tagCatalog == null ? TrustedProblemTagCatalog.empty() : tagCatalog;
    this.metrics = metrics == null ? PracticeSubmissionHistoryToolMetrics.NOOP : metrics;
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    String problemRef = PracticeSubmissionHistoryToolSupport.requiredText(
        arguments, PracticeSubmissionHistoryToolContracts.ARGUMENT_PROBLEM_REF);
    String cursor = PracticeSubmissionHistoryToolSupport.optionalText(
        arguments, PracticeSubmissionHistoryToolContracts.ARGUMENT_CURSOR);
    int limit = PracticeSubmissionHistoryToolSupport.optionalListLimit(arguments);
    if (!PracticeSubmissionHistoryToolSupport.hasOnlyFields(arguments, FIELDS)
        || problemRef == null || cursor == null || limit < 1) {
      return record(PracticeSubmissionHistoryToolSupport.failure(
          PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_LIST,
          PracticeSubmissionHistoryToolContracts.STATUS_FAILED,
          PracticeSubmissionHistoryToolContracts.FAILURE_INVALID_ARGUMENTS));
    }
    PracticeSubmissionHistoryRunScopeRegistry.ScopeUse use = scopeRegistry.reserveList(
        PracticeSubmissionHistoryToolSupport.scopeRef(context), problemRef, cursor);
    if (!use.granted()) {
      metrics.recordScopeRejected(use.status().name());
      return record(PracticeSubmissionHistoryToolSupport.failureForScope(
          PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_LIST, use.status()));
    }
    try {
      PracticeSubmissionHistoryPage page = repository.findSubmissions(
          use.userId(), use.problem().problemSlug(), use.afterCreatedAt(), use.afterReviewId(), limit);
      ObjectNode result = PracticeSubmissionHistoryToolSupport.success(
          PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_LIST);
      result.put(PracticeSubmissionHistoryToolContracts.FIELD_PROBLEM_REF, use.problem().problemRef());
      ArrayNode submissions = result.putArray(PracticeSubmissionHistoryToolContracts.FIELD_SUBMISSIONS);
      List<TrustedProblemTag> catalogTags = tagCatalog.findByProblemSlug(use.problem().problemSlug());
      for (PracticeSubmissionHistoryReview review : page.submissions()) {
        ObjectNode item = submissions.addObject();
        PracticeSubmissionHistoryToolSupport.writeSubmission(
            item,
            use.issueSubmissionRef(review.reviewId()),
            review,
            affectedTags(review, catalogTags),
            false);
      }
      result.put(PracticeSubmissionHistoryToolContracts.FIELD_HAS_MORE, page.hasMore());
      if (page.hasMore() && !page.submissions().isEmpty()) {
        PracticeSubmissionHistoryReview last = page.submissions().get(page.submissions().size() - 1);
        result.put(PracticeSubmissionHistoryToolContracts.FIELD_NEXT_CURSOR,
            use.issueNextCursor(last.submittedAt(), last.reviewId()));
      } else {
        result.putNull(PracticeSubmissionHistoryToolContracts.FIELD_NEXT_CURSOR);
      }
      return record(result);
    } catch (RuntimeException ignored) {
      metrics.recordToolDataAccessFailure();
      return record(PracticeSubmissionHistoryToolSupport.failure(
          PracticeSubmissionHistoryToolContracts.TYPE_SUBMISSION_LIST,
          PracticeSubmissionHistoryToolContracts.STATUS_FAILED,
          PracticeSubmissionHistoryToolContracts.FAILURE_DATA_ACCESS));
    }
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
    metrics.recordToolCall(PracticeSubmissionHistoryToolContracts.LIST_PRACTICE_PROBLEM_SUBMISSIONS,
        result.path(PracticeSubmissionHistoryToolContracts.FIELD_STATUS).asText());
    return result;
  }

  private static ObjectNode schema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    ObjectNode properties = schema.putObject("properties");
    properties.putObject(PracticeSubmissionHistoryToolContracts.ARGUMENT_PROBLEM_REF).put("type", "string");
    properties.putObject(PracticeSubmissionHistoryToolContracts.ARGUMENT_CURSOR)
        .put("type", "string")
        .put("description", "Empty string for the first page; use nextCursor for subsequent pages.");
    properties.putObject(PracticeSubmissionHistoryToolContracts.ARGUMENT_LIMIT).put("type", "integer")
        .put("minimum", 1).put("maximum", PracticeSubmissionHistoryToolContracts.MAX_LIST_LIMIT)
        .put("description", "Number of submissions to return; use 3 as the default.");
    schema.putArray("required")
        .add(PracticeSubmissionHistoryToolContracts.ARGUMENT_PROBLEM_REF)
        .add(PracticeSubmissionHistoryToolContracts.ARGUMENT_CURSOR)
        .add(PracticeSubmissionHistoryToolContracts.ARGUMENT_LIMIT);
    return schema;
  }
}
