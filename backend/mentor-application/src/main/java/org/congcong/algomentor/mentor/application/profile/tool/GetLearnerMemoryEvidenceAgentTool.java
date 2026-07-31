package org.congcong.algomentor.mentor.application.profile.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimMessageEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimReviewEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.repository.LearnerMemoryEvidenceRepository;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallSnapshot;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** 读取当前 snapshot 某 statement 的受限证据摘要，不暴露代码、Review 正文或用户消息正文。 */
public final class GetLearnerMemoryEvidenceAgentTool implements AgentTool {

  private static final Set<String> FIELDS = Set.of(
      LearnerMemoryRecallToolContracts.ARGUMENT_STATEMENT_REF,
      LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT,
      LearnerMemoryRecallToolContracts.ARGUMENT_CURSOR);
  private static final LlmToolSpec SPEC = new LlmToolSpec(
      LearnerMemoryRecallToolContracts.GET_LEARNER_MEMORY_EVIDENCE,
      "Get bounded evidence summaries for one learner-memory statement in this chat run. Never infer missing evidence.",
      schema(),
      true);

  private final LearnerMemoryRunScopeRegistry scopeRegistry;
  private final LearnerMemoryEvidenceRepository evidenceRepository;
  private final LearnerMemoryRecallToolMetrics observability;

  public GetLearnerMemoryEvidenceAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      LearnerMemoryEvidenceRepository evidenceRepository
  ) {
    this(scopeRegistry, evidenceRepository, LearnerMemoryMetrics.NOOP);
  }

  public GetLearnerMemoryEvidenceAgentTool(
      LearnerMemoryRunScopeRegistry scopeRegistry,
      LearnerMemoryEvidenceRepository evidenceRepository,
      LearnerMemoryMetrics metrics
  ) {
    this.scopeRegistry = scopeRegistry;
    this.evidenceRepository = evidenceRepository;
    this.observability = new LearnerMemoryRecallToolMetrics(metrics);
  }

  @Override
  public LlmToolSpec spec() {
    return SPEC;
  }

  @Override
  public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
    int limit = LearnerMemoryRecallToolSupport.requiredLimit(arguments, FIELDS);
    String statementRef = LearnerMemoryRecallToolSupport.requiredText(arguments,
        LearnerMemoryRecallToolContracts.ARGUMENT_STATEMENT_REF);
    String cursor = LearnerMemoryRecallToolSupport.optionalText(arguments, LearnerMemoryRecallToolContracts.ARGUMENT_CURSOR);
    if (limit < 1 || statementRef == null || cursor == null) {
      return observability.record(LearnerMemoryRecallToolContracts.GET_LEARNER_MEMORY_EVIDENCE,
          LearnerMemoryRecallToolSupport.failure(
          LearnerMemoryRecallToolContracts.TYPE_EVIDENCE,
          LearnerMemoryRecallToolContracts.STATUS_FAILED,
          LearnerMemoryRecallToolContracts.FAILURE_INVALID_ARGUMENTS));
    }
    LearnerMemoryRunScopeRegistry.RecallScopeUse use = scopeRegistry.reserveRecallTool(
        LearnerMemoryRecallToolSupport.scopeRef(context));
    if (!use.granted()) {
      return observability.record(LearnerMemoryRecallToolContracts.GET_LEARNER_MEMORY_EVIDENCE,
          LearnerMemoryRecallToolSupport.failureForScope(LearnerMemoryRecallToolContracts.TYPE_EVIDENCE, use.status()));
    }
    ObjectNode result = null;
    try {
      LearnerMemoryRecallSnapshot.Statement statement = LearnerMemoryRecallToolSupport.statement(use.snapshot(), statementRef);
      if (statement == null) {
        result = LearnerMemoryRecallToolSupport.failure(
            LearnerMemoryRecallToolContracts.TYPE_EVIDENCE,
            LearnerMemoryRecallToolContracts.STATUS_FAILED,
            LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE);
      } else {
        java.util.OptionalInt start = LearnerMemoryRecallToolSupport.cursorOffset(
            use, cursor, LearnerMemoryRecallToolContracts.CURSOR_EVIDENCE, statementRef);
        result = start.isEmpty()
            ? LearnerMemoryRecallToolSupport.failure(
                LearnerMemoryRecallToolContracts.TYPE_EVIDENCE,
                LearnerMemoryRecallToolContracts.STATUS_FAILED,
                LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE)
            : page(use, statementRef, evidence(use.userId(), statement.claim().id()), start.getAsInt(), limit);
      }
    } catch (RuntimeException exception) {
      result = LearnerMemoryRecallToolSupport.failure(
          LearnerMemoryRecallToolContracts.TYPE_EVIDENCE,
          LearnerMemoryRecallToolContracts.STATUS_FAILED,
          LearnerMemoryRecallToolContracts.FAILURE_INTERNAL);
    } finally {
      use.complete(result == null ? 0 : result.toString().length());
    }
    return observability.record(LearnerMemoryRecallToolContracts.GET_LEARNER_MEMORY_EVIDENCE, result);
  }

  private List<EvidenceSummary> evidence(long userId, long revisionId) {
    List<EvidenceSummary> summaries = new ArrayList<>();
    for (LearnerMemoryClaimReviewEvidence evidence : evidenceRepository.findReviewEvidenceByRevisionIds(userId, List.of(revisionId))) {
      summaries.add(new EvidenceSummary("FORMAL_REVIEW", evidence.role().name(), evidence.createdAt(), evidence.sequenceNo()));
    }
    for (LearnerMemoryClaimMessageEvidence evidence : evidenceRepository.findMessageEvidenceByRevisionIds(userId, List.of(revisionId))) {
      summaries.add(new EvidenceSummary("USER_MESSAGE", evidence.role().name(), evidence.createdAt(), evidence.sequenceNo()));
    }
    return summaries.stream().sorted(Comparator
        .comparing(EvidenceSummary::recordedAt)
        .thenComparing(EvidenceSummary::source)
        .thenComparingInt(EvidenceSummary::sequenceNo))
        .toList();
  }

  private ObjectNode page(
      LearnerMemoryRunScopeRegistry.RecallScopeUse use,
      String statementRef,
      List<EvidenceSummary> evidence,
      int start,
      int limit
  ) {
    if (start < 0 || start > evidence.size()) {
      return LearnerMemoryRecallToolSupport.failure(
          LearnerMemoryRecallToolContracts.TYPE_EVIDENCE,
          LearnerMemoryRecallToolContracts.STATUS_FAILED,
          LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE);
    }
    ObjectNode result = LearnerMemoryRecallToolSupport.success(LearnerMemoryRecallToolContracts.TYPE_EVIDENCE);
    result.put(LearnerMemoryRecallToolContracts.FIELD_STATEMENT_REF, statementRef);
    ArrayNode items = LearnerMemoryRecallToolSupport.items(result);
    int next = start;
    while (next < evidence.size() && items.size() < limit) {
      EvidenceSummary value = evidence.get(next);
      items.addObject()
          .put(LearnerMemoryRecallToolContracts.FIELD_EVIDENCE_SOURCE, value.source())
          .put(LearnerMemoryRecallToolContracts.FIELD_EVIDENCE_ROLE, value.role())
          .put(LearnerMemoryRecallToolContracts.FIELD_RECORDED_AT, value.recordedAt().toString());
      if (!LearnerMemoryRecallToolSupport.fits(result, use.maxVisibleChars())) {
        items.remove(items.size() - 1);
        break;
      }
      next++;
    }
    if (next < evidence.size()) {
      String nextCursor = use.createCursor(LearnerMemoryRecallToolContracts.CURSOR_EVIDENCE, statementRef, next);
      result.put(LearnerMemoryRecallToolContracts.FIELD_NEXT_CURSOR, nextCursor);
      if (!LearnerMemoryRecallToolSupport.fits(result, use.maxVisibleChars()) && items.size() > 0) {
        items.remove(items.size() - 1);
        next--;
        result.put(LearnerMemoryRecallToolContracts.FIELD_NEXT_CURSOR,
            use.createCursor(LearnerMemoryRecallToolContracts.CURSOR_EVIDENCE, statementRef, next));
      }
    }
    return result;
  }

  private static ObjectNode schema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    ObjectNode properties = schema.putObject("properties");
    properties.putObject(LearnerMemoryRecallToolContracts.ARGUMENT_STATEMENT_REF).put("type", "string");
    properties.putObject(LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT).put("type", "integer").put("minimum", 1)
        .put("maximum", LearnerMemoryRecallToolContracts.MAX_ITEMS);
    properties.putObject(LearnerMemoryRecallToolContracts.ARGUMENT_CURSOR).put("type", "string");
    schema.putArray("required")
        .add(LearnerMemoryRecallToolContracts.ARGUMENT_STATEMENT_REF)
        .add(LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT);
    return schema;
  }

  private record EvidenceSummary(String source, String role, Instant recordedAt, int sequenceNo) {
  }
}
