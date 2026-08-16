package org.congcong.algomentor.mentor.application.practice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;

/** 读取当前 run 已授权历史题的跨计划正式提交聚合，不返回源码或逐次反馈。 */
public final class GetPracticedProblemOverviewAgentTool implements AgentTool {

  private static final Set<String> FIELDS = Set.of(PracticeSubmissionHistoryToolContracts.ARGUMENT_PROBLEM_REF);
  private static final LlmToolSpec SPEC = new LlmToolSpec(
      PracticeSubmissionHistoryToolContracts.GET_PRACTICED_PROBLEM_OVERVIEW,
      "Get the scoped problem's formal submission count and latest conclusion without returning source code or review Markdown.",
      schema(),
      true);

  private final PracticeSubmissionHistoryRunScopeRegistry scopeRegistry;
  private final PracticeSubmissionHistoryToolRepository repository;
  private final PracticeSubmissionHistoryToolMetrics metrics;

  public GetPracticedProblemOverviewAgentTool(
      PracticeSubmissionHistoryRunScopeRegistry scopeRegistry,
      PracticeSubmissionHistoryToolRepository repository,
      PracticeSubmissionHistoryToolMetrics metrics
  ) {
    this.scopeRegistry = java.util.Objects.requireNonNull(scopeRegistry, "scopeRegistry must not be null");
    this.repository = java.util.Objects.requireNonNull(repository, "repository must not be null");
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
    if (!PracticeSubmissionHistoryToolSupport.hasOnlyFields(arguments, FIELDS) || problemRef == null) {
      return record(PracticeSubmissionHistoryToolSupport.failure(
          PracticeSubmissionHistoryToolContracts.TYPE_OVERVIEW,
          PracticeSubmissionHistoryToolContracts.STATUS_FAILED,
          PracticeSubmissionHistoryToolContracts.FAILURE_INVALID_ARGUMENTS));
    }
    PracticeSubmissionHistoryRunScopeRegistry.ScopeUse use = scopeRegistry.reserveOverview(
        PracticeSubmissionHistoryToolSupport.scopeRef(context), problemRef);
    if (!use.granted()) {
      metrics.recordScopeRejected(use.status().name());
      return record(PracticeSubmissionHistoryToolSupport.failureForScope(
          PracticeSubmissionHistoryToolContracts.TYPE_OVERVIEW, use.status()));
    }
    try {
      return repository.findOverview(use.userId(), use.problem().problemSlug())
          .<JsonNode>map(overview -> {
            ObjectNode result = PracticeSubmissionHistoryToolSupport.success(
                PracticeSubmissionHistoryToolContracts.TYPE_OVERVIEW);
            ObjectNode problem = result.putObject(PracticeSubmissionHistoryToolContracts.FIELD_PROBLEM);
            PracticeSubmissionHistoryToolSupport.writeProblem(problem, use.problem());
            problem.put(PracticeSubmissionHistoryToolContracts.FIELD_FORMAL_SUBMISSION_COUNT,
                overview.formalSubmissionCount());
            problem.put(PracticeSubmissionHistoryToolContracts.FIELD_PASSED_SUBMISSION_COUNT,
                overview.passedSubmissionCount());
            problem.put(PracticeSubmissionHistoryToolContracts.FIELD_FIRST_SUBMITTED_AT,
                overview.firstSubmittedAt().toString());
            ObjectNode latest = problem.putObject(PracticeSubmissionHistoryToolContracts.FIELD_LATEST_SUBMISSION);
            PracticeSubmissionHistoryToolSupport.writeOverviewLatestSubmission(
                latest, use.issueSubmissionRef(overview.latestSubmission().reviewId()), overview.latestSubmission());
            return result;
          })
          .orElseGet(() -> record(PracticeSubmissionHistoryToolSupport.failure(
              PracticeSubmissionHistoryToolContracts.TYPE_OVERVIEW,
              PracticeSubmissionHistoryToolContracts.STATUS_UNAVAILABLE,
              PracticeSubmissionHistoryToolContracts.FAILURE_UNAVAILABLE)));
    } catch (RuntimeException ignored) {
      metrics.recordToolDataAccessFailure();
      return record(PracticeSubmissionHistoryToolSupport.failure(
          PracticeSubmissionHistoryToolContracts.TYPE_OVERVIEW,
          PracticeSubmissionHistoryToolContracts.STATUS_FAILED,
          PracticeSubmissionHistoryToolContracts.FAILURE_DATA_ACCESS));
    }
  }

  private JsonNode record(JsonNode result) {
    metrics.recordToolCall(PracticeSubmissionHistoryToolContracts.GET_PRACTICED_PROBLEM_OVERVIEW,
        result.path(PracticeSubmissionHistoryToolContracts.FIELD_STATUS).asText());
    return result;
  }

  private static ObjectNode schema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putObject("properties").putObject(PracticeSubmissionHistoryToolContracts.ARGUMENT_PROBLEM_REF)
        .put("type", "string");
    schema.putArray("required").add(PracticeSubmissionHistoryToolContracts.ARGUMENT_PROBLEM_REF);
    return schema;
  }
}
