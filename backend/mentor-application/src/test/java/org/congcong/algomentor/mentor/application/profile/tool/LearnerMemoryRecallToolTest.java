package org.congcong.algomentor.mentor.application.profile.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactor;
import org.congcong.algomentor.agent.core.tool.ReadToolResultTool;
import org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore;
import org.congcong.algomentor.agent.core.toolresult.ToolResultProvenance;
import org.congcong.algomentor.agent.core.toolresult.ToolResultReadGuard;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimMessageEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryClaimReviewEvidence;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.repository.LearnerMemoryEvidenceRepository;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallSnapshot;
import org.junit.jupiter.api.Test;

class LearnerMemoryRecallToolTest {

  @Test
  void exposesStrictSchemasWithEveryPropertyRequired() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    SearchLearnerMemoryAgentTool search = new SearchLearnerMemoryAgentTool(registry);
    ReadLearnerMemorySectionAgentTool readSection = new ReadLearnerMemorySectionAgentTool(registry);
    GetLearnerMemoryEvidenceAgentTool evidence = new GetLearnerMemoryEvidenceAgentTool(
        registry, new EvidenceRepository(List.of(), List.of()));

    assertThat(search.spec().inputSchema().path("required"))
        .extracting(JsonNode::asText)
        .containsExactly(
            LearnerMemoryRecallToolContracts.ARGUMENT_QUERY,
            LearnerMemoryRecallToolContracts.ARGUMENT_SECTION_REF,
            LearnerMemoryRecallToolContracts.ARGUMENT_TAG_VALUES,
            LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT,
            LearnerMemoryRecallToolContracts.ARGUMENT_CURSOR);
    assertThat(readSection.spec().inputSchema().path("required"))
        .extracting(JsonNode::asText)
        .containsExactly(
            LearnerMemoryRecallToolContracts.ARGUMENT_SECTION_REF,
            LearnerMemoryRecallToolContracts.ARGUMENT_AFTER_STATEMENT_REF,
            LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT);
    assertThat(evidence.spec().inputSchema().path("required"))
        .extracting(JsonNode::asText)
        .containsExactly(
            LearnerMemoryRecallToolContracts.ARGUMENT_STATEMENT_REF,
            LearnerMemoryRecallToolContracts.ARGUMENT_LIMIT,
            LearnerMemoryRecallToolContracts.ARGUMENT_CURSOR);
  }

  @Test
  void pagesOnlyCurrentScopeAndRejectsForgedReferencesWithoutLeaking() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryRunScopeRegistry.RecallScopeLease lease = openScope(registry, 7L, List.of(
        claim(1L, 7L, "数组题经常忽略边界条件"),
        claim(2L, 7L, "数组题会先确认循环不变量")));
    LearnerMemoryRunScopeRegistry.RecallScopeLease other = openScope(registry, 8L, List.of(
        claim(3L, 8L, "其他用户的私有目标")));
    SearchLearnerMemoryAgentTool search = new SearchLearnerMemoryAgentTool(registry);
    ReadLearnerMemorySectionAgentTool readSection = new ReadLearnerMemorySectionAgentTool(registry);

    JsonNode searchResult = search.execute(searchArguments("数组", 1), context(lease));
    String cursor = searchResult.path(LearnerMemoryRecallToolContracts.FIELD_NEXT_CURSOR).asText();
    assertThat(searchResult.path(LearnerMemoryRecallToolContracts.FIELD_ITEMS)).hasSize(1);
    assertThat(cursor).hasSize(43);

    JsonNode nextPage = search.execute(searchArguments("数组", 20).put("cursor", cursor), context(lease));
    assertThat(nextPage.path(LearnerMemoryRecallToolContracts.FIELD_ITEMS)).hasSize(1);

    String sectionRef = lease.snapshot().sections().get(0).sectionRef();
    JsonNode section = readSection.execute(sectionArguments(sectionRef, 1), context(lease));
    String afterStatementRef = section.path("nextAfterStatementRef").asText();
    assertThat(afterStatementRef).hasSize(43);

    LearnerMemoryRunScopeRegistry.RecallScopeLease forgedLease = openScope(registry, 7L, List.of(
        claim(4L, 7L, "当前用户的本地快照")));
    JsonNode forged = search.execute(searchArguments("数组", 1)
        .put("sectionRef", other.snapshot().sections().get(0).sectionRef()), context(forgedLease));
    assertThat(forged.path(LearnerMemoryRecallToolContracts.FIELD_FAILURE_CODE).asText())
        .isEqualTo(LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE);
    assertThat(forged.toString()).doesNotContain("其他用户的私有目标");

    LearnerMemoryRunScopeRegistry.RecallScopeLease forgedCursorLease = openScope(registry, 7L, List.of(
        claim(5L, 7L, "当前用户的另一个本地快照")));
    JsonNode forgedCursor = search.execute(searchArguments("数组", 1).put("cursor", "forged-cursor"),
        context(forgedCursorLease));
    assertThat(forgedCursor.path(LearnerMemoryRecallToolContracts.FIELD_FAILURE_CODE).asText())
        .isEqualTo(LearnerMemoryRecallToolContracts.FAILURE_NOT_FOUND_OR_NOT_READABLE);
  }

  @Test
  void returnsRedactedEvidenceSummariesAndEnforcesSharedBusinessBudget() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryClaimRevision claim = claim(11L, 7L, "准备后端面试");
    LearnerMemoryRunScopeRegistry.RecallScopeLease lease = openScope(registry, 7L, List.of(claim));
    GetLearnerMemoryEvidenceAgentTool evidenceTool = new GetLearnerMemoryEvidenceAgentTool(
        registry,
        new EvidenceRepository(List.of(
            new LearnerMemoryClaimReviewEvidence(11L, 101L, LearnerMemoryEvidenceContract.ReviewRole.OBSERVED, 1, NOW)),
        List.of(new LearnerMemoryClaimMessageEvidence(
            11L, 301L, LearnerMemoryEvidenceContract.MessageRole.DECLARED, 1, NOW))));
    String statementRef = lease.snapshot().sections().get(0).statements().get(0).statementRef();

    JsonNode evidence = evidenceTool.execute(evidenceArguments(statementRef, 20), context(lease));
    assertThat(evidence.path(LearnerMemoryRecallToolContracts.FIELD_ITEMS)).hasSize(2);
    assertThat(evidence.toString()).doesNotContain("101", "301", "normalizedCode", "reviewMarkdown");

    SearchLearnerMemoryAgentTool search = new SearchLearnerMemoryAgentTool(registry);
    search.execute(searchArguments("后端", 20), context(lease));
    search.execute(searchArguments("面试", 20), context(lease));
    JsonNode exhausted = search.execute(searchArguments("准备", 20), context(lease));
    assertThat(exhausted.path(LearnerMemoryRecallToolContracts.FIELD_STATUS).asText())
        .isEqualTo(LearnerMemoryRecallToolContracts.STATUS_BUDGET_EXHAUSTED);
  }

  @Test
  void guardCountsOnlyMemoryResultReadsAndSharesVisibleCharacterBudget() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryRunScopeRegistry.RecallScopeLease lease = openScope(registry, 7L, List.of(claim(21L, 7L, "稳定性")));
    LearnerMemoryToolResultReadGuard guard = new LearnerMemoryToolResultReadGuard(registry);
    AgentExecutionContext context = context(lease);

    ToolResultReadGuard.ToolResultReadPermit generic = guard.beforeRead(
        context, new ToolResultProvenance(1, "call-generic", "calculator"), 8_000);
    assertThat(generic.allowed()).isTrue();
    guard.afterRead(generic, 8_000);

    ToolResultReadGuard.ToolResultReadPermit first = guard.beforeRead(
        context, new ToolResultProvenance(1, "call-memory-1", LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY), 8_000);
    ToolResultReadGuard.ToolResultReadPermit second = guard.beforeRead(
        context, new ToolResultProvenance(2, "call-memory-2", LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY), 8_000);
    assertThat(first.maxVisibleChars()).isEqualTo(8_000);
    assertThat(second.maxVisibleChars()).isEqualTo(8_000);
    guard.afterRead(first, 8_000);
    guard.afterRead(second, 8_000);

    ToolResultReadGuard.ToolResultReadPermit third = guard.beforeRead(
        context, new ToolResultProvenance(3, "call-memory-3", LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY), 8_000);
    assertThat(third.allowed()).isFalse();
    assertThat(third.rejectionType()).isEqualTo(LearnerMemoryRecallToolContracts.STATUS_BUDGET_EXHAUSTED);

    LearnerMemoryRunScopeRegistry.RecallScopeLease totalLease = openScope(registry, 7L, List.of(claim(22L, 7L, "总量预算")));
    for (int index = 0; index < LearnerMemoryRecallToolContracts.MAX_BUSINESS_TOOL_CALLS; index++) {
      LearnerMemoryRunScopeRegistry.RecallScopeUse use = registry.reserveRecallTool(totalLease.scopeRef());
      assertThat(use.granted()).isTrue();
      use.complete(8_000);
    }
    ToolResultReadGuard.ToolResultReadPermit totalExhausted = guard.beforeRead(
        context(totalLease), new ToolResultProvenance(4, "call-memory-4",
            LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY), 8_000);
    assertThat(totalExhausted.allowed()).isFalse();
    assertThat(totalExhausted.rejectionType()).isEqualTo(LearnerMemoryRecallToolContracts.STATUS_BUDGET_EXHAUSTED);
  }

  @Test
  void pagesCompleteClaimsWithinTheSingleResultLimitAndStatesEmptySearchScope() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    List<LearnerMemoryClaimRevision> claims = java.util.stream.LongStream.rangeClosed(1, 20)
        .mapToObj(id -> claim(id, 7L, "x".repeat(600)))
        .toList();
    LearnerMemoryRunScopeRegistry.RecallScopeLease lease = openScope(registry, 7L, claims);
    SearchLearnerMemoryAgentTool search = new SearchLearnerMemoryAgentTool(registry);

    JsonNode firstPage = search.execute(searchArguments("x", 20), context(lease));

    assertThat(firstPage.toString().length()).isLessThanOrEqualTo(LearnerMemoryRecallToolContracts.MAX_RESULT_CHARS);
    assertThat(firstPage.path(LearnerMemoryRecallToolContracts.FIELD_ITEMS)).isNotEmpty();
    assertThat(firstPage.path(LearnerMemoryRecallToolContracts.FIELD_ITEMS)).allSatisfy(item ->
        assertThat(item.path(LearnerMemoryRecallToolContracts.FIELD_CLAIM_TEXT).asText()).hasSize(600));
    assertThat(firstPage.path(LearnerMemoryRecallToolContracts.FIELD_NEXT_CURSOR).asText()).isNotBlank();

    JsonNode secondPage = search.execute(searchArguments("x", 20)
        .put(LearnerMemoryRecallToolContracts.ARGUMENT_CURSOR,
            firstPage.path(LearnerMemoryRecallToolContracts.FIELD_NEXT_CURSOR).asText()), context(lease));

    assertThat(secondPage.toString().length()).isLessThanOrEqualTo(LearnerMemoryRecallToolContracts.MAX_RESULT_CHARS);
    assertThat(secondPage.path(LearnerMemoryRecallToolContracts.FIELD_ITEMS)).isNotEmpty();
    assertThat(secondPage.path(LearnerMemoryRecallToolContracts.FIELD_ITEMS)).allSatisfy(item ->
        assertThat(item.path(LearnerMemoryRecallToolContracts.FIELD_CLAIM_TEXT).asText()).hasSize(600));

    JsonNode empty = search.execute(searchArguments("unrelated", 20), context(lease));
    assertThat(empty.path(LearnerMemoryRecallToolContracts.FIELD_MESSAGE).asText())
        .isEqualTo(LearnerMemoryRecallToolContracts.MESSAGE_EMPTY_SEARCH);
  }

  @Test
  void permitsCompactedMemoryResultRangesOnlyForTheOwningRun() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryRunScopeRegistry.RecallScopeLease lease = openScope(
        registry, 7L, List.of(claim(31L, 7L, "长结果读取")));
    InMemoryToolResultStore store = new InMemoryToolResultStore();
    ToolResultCompactionPolicy policy = policy();
    AgentLoopContext loopContext = loopContext(lease, "memory-run");
    ToolResultCompactor compactor = new ToolResultCompactor(new ObjectMapper(), policy, store);
    JsonNode visible = compactor.compactForModel(
        loopContext,
        1,
        new LlmToolCall(
            "memory-call",
            LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY,
            JsonNodeFactory.instance.objectNode()),
        JsonNodeFactory.instance.objectNode().put("payload", "x".repeat(17_000))).visibleResult();
    ReadToolResultTool read = new ReadToolResultTool(store, policy, new LearnerMemoryToolResultReadGuard(registry));
    String resultRef = visible.path("resultRef").asText();

    assertThat(visible.path("type").asText()).isEqualTo("tool_result_preview");
    assertThat(read.execute(rangeArguments(resultRef, 0), context(lease, "memory-run")).path("content").asText())
        .hasSize(LearnerMemoryRecallToolContracts.MAX_RESULT_CHARS);
    assertThat(read.execute(rangeArguments(resultRef, LearnerMemoryRecallToolContracts.MAX_RESULT_CHARS),
        context(lease, "memory-run")).path("content").asText())
        .hasSize(LearnerMemoryRecallToolContracts.MAX_RESULT_CHARS);
    JsonNode exhausted = read.execute(rangeArguments(resultRef, 16_000), context(lease, "memory-run"));
    assertThat(exhausted.path("type").asText())
        .isEqualTo(LearnerMemoryRecallToolContracts.STATUS_BUDGET_EXHAUSTED);
    assertThatThrownBy(() -> read.execute(rangeArguments(resultRef, 0), context(lease, "other-run")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found or is not readable");
  }

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private static LearnerMemoryRunScopeRegistry.RecallScopeLease openScope(
      LearnerMemoryRunScopeRegistry registry,
      long userId,
      List<LearnerMemoryClaimRevision> claims
  ) {
    return registry.openRecallScope(
        userId,
        "a".repeat(64),
        "zh-CN",
        List.of(new LearnerMemoryRecallSnapshot.SectionInput(
            "background-goals",
            "学习背景与目标",
            claims.stream().map(claim -> new LearnerMemoryRecallSnapshot.StatementInput(claim, "用户明确自述", false)).toList())),
        List.of());
  }

  private static LearnerMemoryClaimRevision claim(long id, long userId, String text) {
    return new LearnerMemoryClaimRevision(
        id,
        new UUID(0L, id),
        userId,
        new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.DECLARED_FACT,
            LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS,
            null),
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        text,
        "a".repeat(64),
        LearnerMemoryClaimContract.Origin.USER_EXPLICIT,
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED,
        null,
        1L,
        null,
        NOW,
        null,
        NOW,
        NOW);
  }

  private static AgentExecutionContext context(LearnerMemoryRunScopeRegistry.RecallScopeLease lease) {
    return context(lease, "run-" + lease.scopeRef());
  }

  private static AgentExecutionContext context(
      LearnerMemoryRunScopeRegistry.RecallScopeLease lease,
      String runId
  ) {
    return new AgentExecutionContext(
        runId,
        1,
        java.util.Map.of(LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF, lease.scopeRef()),
        false);
  }

  private static AgentLoopContext loopContext(
      LearnerMemoryRunScopeRegistry.RecallScopeLease lease,
      String runId
  ) {
    java.util.Map<String, Object> metadata = java.util.Map.of(
        LearnerMemoryAgentToolContracts.METADATA_SCOPE_REF, lease.scopeRef());
    AgentRequest request = new AgentRequest(runId, runId, List.of(LlmMessage.user("memory")), metadata);
    return new AgentLoopContext(runId, request, 1, metadata);
  }

  private static com.fasterxml.jackson.databind.node.ObjectNode searchArguments(String query, int limit) {
    return JsonNodeFactory.instance.objectNode().put("query", query).put("limit", limit);
  }

  private static com.fasterxml.jackson.databind.node.ObjectNode sectionArguments(String sectionRef, int limit) {
    return JsonNodeFactory.instance.objectNode().put("sectionRef", sectionRef).put("limit", limit);
  }

  private static com.fasterxml.jackson.databind.node.ObjectNode evidenceArguments(String statementRef, int limit) {
    return JsonNodeFactory.instance.objectNode().put("statementRef", statementRef).put("limit", limit);
  }

  private static com.fasterxml.jackson.databind.node.ObjectNode rangeArguments(String resultRef, int offset) {
    return JsonNodeFactory.instance.objectNode()
        .put("resultRef", resultRef)
        .put("offset", offset)
        .put("limit", LearnerMemoryRecallToolContracts.MAX_RESULT_CHARS);
  }

  private static ToolResultCompactionPolicy policy() {
    return new ToolResultCompactionPolicy(
        100,
        64,
        LearnerMemoryRecallToolContracts.MAX_RESULT_CHARS,
        true,
        24_000,
        3,
        true,
        120_000,
        80,
        2,
        24,
        true,
        false);
  }

  private record EvidenceRepository(
      List<LearnerMemoryClaimReviewEvidence> reviewEvidence,
      List<LearnerMemoryClaimMessageEvidence> messageEvidence
  ) implements LearnerMemoryEvidenceRepository {

    @Override
    public List<LearnerMemoryClaimReviewEvidence> findReviewEvidenceByRevisionIds(long userId, Collection<Long> revisionIds) {
      return reviewEvidence.stream().filter(value -> revisionIds.contains(value.claimRevisionId())).toList();
    }

    @Override
    public List<LearnerMemoryClaimMessageEvidence> findMessageEvidenceByRevisionIds(long userId, Collection<Long> revisionIds) {
      return messageEvidence.stream().filter(value -> revisionIds.contains(value.claimRevisionId())).toList();
    }

    @Override
    public Set<Long> findOwnedReviewIds(long userId, Collection<Long> reviewIds) {
      return Set.of();
    }

    @Override
    public Set<Long> findOwnedMessageIds(long userId, Collection<Long> messageIds) {
      return Set.of();
    }

    @Override
    public void insertReviewEvidence(Collection<LearnerMemoryClaimReviewEvidence> evidence) {
    }

    @Override
    public void insertMessageEvidence(Collection<LearnerMemoryClaimMessageEvidence> evidence) {
    }
  }
}
