package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactor;
import org.congcong.algomentor.agent.core.tool.ReadToolResultTool;
import org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore;
import org.congcong.algomentor.agent.core.toolresult.ToolResultProvenance;
import org.congcong.algomentor.agent.core.toolresult.ToolResultReadGuard;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.junit.jupiter.api.Test;

class PracticeSubmissionHistoryAgentToolTest {

  @Test
  void exposesOnlyStrictOpaqueReferenceSchemas() {
    PracticeSubmissionHistoryRunScopeRegistry registry = new PracticeSubmissionHistoryRunScopeRegistry();
    RecordingRepository repository = new RecordingRepository();
    GetPracticedProblemOverviewAgentTool overview = new GetPracticedProblemOverviewAgentTool(
        registry, repository, PracticeSubmissionHistoryToolMetrics.NOOP);
    ListPracticeProblemSubmissionsAgentTool list = new ListPracticeProblemSubmissionsAgentTool(
        registry, repository, TrustedProblemTagCatalog.empty(), PracticeSubmissionHistoryToolMetrics.NOOP);
    ReadPracticeSubmissionDetailAgentTool detail = new ReadPracticeSubmissionDetailAgentTool(
        registry, repository, TrustedProblemTagCatalog.empty(), ToolResultCompactionPolicy.defaults(),
        PracticeSubmissionHistoryToolMetrics.NOOP);

    assertThat(overview.spec().strict()).isTrue();
    assertThat(list.spec().strict()).isTrue();
    assertThat(detail.spec().strict()).isTrue();
    assertThat(overview.spec().inputSchema().path("additionalProperties").asBoolean()).isFalse();
    assertThat(list.spec().inputSchema().path("additionalProperties").asBoolean()).isFalse();
    assertThat(detail.spec().inputSchema().path("additionalProperties").asBoolean()).isFalse();
    assertThat(overview.spec().inputSchema().path("required"))
        .extracting(JsonNode::asText)
        .containsExactly(PracticeSubmissionHistoryToolContracts.ARGUMENT_PROBLEM_REF);
    assertThat(list.spec().inputSchema().path("required"))
        .extracting(JsonNode::asText)
        .containsExactly(
            PracticeSubmissionHistoryToolContracts.ARGUMENT_PROBLEM_REF,
            PracticeSubmissionHistoryToolContracts.ARGUMENT_CURSOR,
            PracticeSubmissionHistoryToolContracts.ARGUMENT_LIMIT);
    assertThat(detail.spec().inputSchema().path("required"))
        .extracting(JsonNode::asText)
        .containsExactly(PracticeSubmissionHistoryToolContracts.ARGUMENT_SUBMISSION_REF);
    assertThat(list.spec().inputSchema().path("properties")
        .path(PracticeSubmissionHistoryToolContracts.ARGUMENT_CURSOR).path("type").asText())
        .isEqualTo("string");
    assertThat(list.spec().inputSchema().path("properties")
        .path(PracticeSubmissionHistoryToolContracts.ARGUMENT_LIMIT).path("type").asText())
        .isEqualTo("integer");
    assertThat(overview.spec().inputSchema().toString()
        + list.spec().inputSchema()
        + detail.spec().inputSchema())
        .doesNotContain("userId", "problemSlug", "reviewId", "planId", "phaseIndex", "sessionId");
  }

  @Test
  void recognizesOnlyExplicitHistoricalCodeRequests() {
    assertThat(PracticeSubmissionHistoryCodeIntent.hasExplicitCodeRequest("看我之前的代码")).isTrue();
    assertThat(PracticeSubmissionHistoryCodeIntent.hasExplicitCodeRequest("我之前的题目的代码是怎么写的")).isTrue();
    assertThat(PracticeSubmissionHistoryCodeIntent.hasExplicitCodeRequest("请复盘之前提交的代码哪里错了")).isTrue();
    assertThat(PracticeSubmissionHistoryCodeIntent.hasExplicitCodeRequest(
        "Please show my previous implementation.")).isTrue();
    assertThat(PracticeSubmissionHistoryCodeIntent.hasExplicitCodeRequest("我之前哪里错了？")).isFalse();
    assertThat(PracticeSubmissionHistoryCodeIntent.hasExplicitCodeRequest("I am stuck on this problem.")).isFalse();
  }

  @Test
  void overviewRejectsForgedAndRepeatedRefsWithoutQueryingAndNeverLeaksTheSlug() {
    PracticeSubmissionHistoryRunScopeRegistry registry = new PracticeSubmissionHistoryRunScopeRegistry();
    PracticeSubmissionHistoryRunScopeRegistry.ScopeLease lease = registry.openScope(
        7L, "zh-CN", List.of(scopeInput("pp_history", "two-sum")));
    RecordingRepository repository = new RecordingRepository();
    repository.overview = Optional.of(overview());
    GetPracticedProblemOverviewAgentTool tool = new GetPracticedProblemOverviewAgentTool(
        registry, repository, PracticeSubmissionHistoryToolMetrics.NOOP);

    JsonNode forged = tool.execute(arguments("problemRef", "pp_forged"), context(lease.scopeRef(), false));
    assertThat(forged.path("status").asText()).isEqualTo("UNAVAILABLE");
    assertThat(repository.overviewCalls).isZero();

    JsonNode first = tool.execute(arguments("problemRef", "pp_history"), context(lease.scopeRef(), false));
    assertThat(first.path("status").asText()).isEqualTo("OK");
    assertThat(first.toString()).doesNotContain("two-sum").doesNotContain("normalizedCode");
    assertThat(first.path("problem").path("latestSubmission").path("submissionRef").asText()).startsWith("ps_");

    JsonNode repeated = tool.execute(arguments("problemRef", "pp_history"), context(lease.scopeRef(), false));
    assertThat(repeated.path("status").asText()).isEqualTo("BUDGET_EXHAUSTED");
    assertThat(repository.overviewCalls).isEqualTo(1);
    assertThat(repository.lastUserId).isEqualTo(7L);
    assertThat(repository.lastProblemSlug).isEqualTo("two-sum");
  }

  @Test
  void listUsesScopedKeysetCursorAndEnforcesTheSharedCallBudget() {
    PracticeSubmissionHistoryRunScopeRegistry registry = new PracticeSubmissionHistoryRunScopeRegistry();
    PracticeSubmissionHistoryRunScopeRegistry.ScopeLease lease = registry.openScope(
        7L, "zh-CN", List.of(scopeInput("pp_history", "two-sum")));
    RecordingRepository repository = new RecordingRepository();
    PracticeSubmissionHistoryReview newer = review(11L, "2026-08-13T11:42:00Z", true);
    PracticeSubmissionHistoryReview older = review(10L, "2026-08-02T10:10:00Z", false);
    repository.firstPage = new PracticeSubmissionHistoryPage(List.of(newer, older), true);
    repository.nextPage = new PracticeSubmissionHistoryPage(List.of(), false);
    ListPracticeProblemSubmissionsAgentTool tool = new ListPracticeProblemSubmissionsAgentTool(
        registry, repository, ignored -> List.of(new TrustedProblemTag(1L, "PREFIX_SUM", "Prefix Sum", "前缀和")),
        PracticeSubmissionHistoryToolMetrics.NOOP);

    JsonNode first = tool.execute(arguments("problemRef", "pp_history"), context(lease.scopeRef(), false));
    String cursor = first.path("nextCursor").asText();
    assertThat(first.path("status").asText()).isEqualTo("OK");
    assertThat(first.path("submissions").get(0).path("submissionRef").asText()).startsWith("ps_");
    assertThat(first.toString()).doesNotContain("normalizedCode").doesNotContain("reviewMarkdown");

    JsonNode second = tool.execute(arguments("problemRef", "pp_history", "cursor", cursor), context(lease.scopeRef(), false));
    assertThat(second.path("status").asText()).isEqualTo("OK");
    JsonNode third = tool.execute(arguments("problemRef", "pp_history"), context(lease.scopeRef(), false));
    assertThat(third.path("status").asText()).isEqualTo("BUDGET_EXHAUSTED");

    assertThat(repository.listCalls).isEqualTo(2);
    assertThat(repository.lastAfterCreatedAt).isEqualTo(older.submittedAt());
    assertThat(repository.lastAfterReviewId).isEqualTo(10L);
    assertThat(repository.lastLimit).isEqualTo(3);
  }

  @Test
  void detailRequiresExplicitIntentConsumesOneCallAndLimitsResultReads() {
    PracticeSubmissionHistoryRunScopeRegistry registry = new PracticeSubmissionHistoryRunScopeRegistry();
    PracticeSubmissionHistoryRunScopeRegistry.ScopeLease lease = registry.openScope(
        7L, "zh-CN", List.of(scopeInput("pp_history", "two-sum")));
    PracticeSubmissionHistoryRunScopeRegistry.ScopeUse signed = registry.reserveOverview(lease.scopeRef(), "pp_history");
    String submissionRef = signed.issueSubmissionRef(11L);
    RecordingRepository repository = new RecordingRepository();
    repository.detail = Optional.of(new PracticeSubmissionHistoryDetail(
        review(11L, "2026-08-13T11:42:00Z", true), "x".repeat(13_000)));
    ReadPracticeSubmissionDetailAgentTool tool = new ReadPracticeSubmissionDetailAgentTool(
        registry,
        repository,
        TrustedProblemTagCatalog.empty(),
        ToolResultCompactionPolicy.defaults(),
        PracticeSubmissionHistoryToolMetrics.NOOP);

    JsonNode noIntent = tool.execute(arguments("submissionRef", submissionRef), context(lease.scopeRef(), false));
    assertThat(noIntent.path("status").asText()).isEqualTo("USER_INTENT_REQUIRED");
    assertThat(repository.detailCalls).isZero();

    JsonNode detail = tool.execute(arguments("submissionRef", submissionRef), context(lease.scopeRef(), true));
    assertThat(detail.path("status").asText()).isEqualTo("OK");
    assertThat(detail.path("submission").path("reviewedCode").asText()).hasSize(13_000);
    assertThat(detail.toString()).doesNotContain("two-sum").doesNotContain("reviewMarkdown");
    JsonNode secondDetail = tool.execute(arguments("submissionRef", submissionRef), context(lease.scopeRef(), true));
    assertThat(secondDetail.path("status").asText()).isEqualTo("BUDGET_EXHAUSTED");

    PracticeSubmissionHistoryToolResultReadGuard guard = new PracticeSubmissionHistoryToolResultReadGuard(
        registry, PracticeSubmissionHistoryToolMetrics.NOOP);
    ToolResultReadGuard.ToolResultReadPermit firstRead = guard.beforeRead(
        context(lease.scopeRef(), true),
        new ToolResultProvenance(1, "call-1", PracticeSubmissionHistoryToolContracts.READ_PRACTICE_SUBMISSION_DETAIL),
        8_000);
    assertThat(firstRead.allowed()).isTrue();
    assertThat(firstRead.maxVisibleChars()).isEqualTo(8_000);
    guard.afterRead(firstRead, 8_000);
    ToolResultReadGuard.ToolResultReadPermit secondRead = guard.beforeRead(
        context(lease.scopeRef(), true),
        new ToolResultProvenance(2, "call-2", PracticeSubmissionHistoryToolContracts.READ_PRACTICE_SUBMISSION_DETAIL),
        8_000);
    assertThat(secondRead.allowed()).isTrue();
    assertThat(secondRead.maxVisibleChars()).isEqualTo(6_000);
    guard.afterRead(secondRead, 6_000);
    ToolResultReadGuard.ToolResultReadPermit thirdRead = guard.beforeRead(
        context(lease.scopeRef(), true),
        new ToolResultProvenance(3, "call-3", PracticeSubmissionHistoryToolContracts.READ_PRACTICE_SUBMISSION_DETAIL),
        1);
    assertThat(thirdRead.allowed()).isFalse();
    assertThat(thirdRead.rejectionType()).isEqualTo("BUDGET_EXHAUSTED");

    lease.release();
    ToolResultReadGuard.ToolResultReadPermit released = guard.beforeRead(
        context(lease.scopeRef(), true),
        new ToolResultProvenance(4, "call-4", PracticeSubmissionHistoryToolContracts.READ_PRACTICE_SUBMISSION_DETAIL),
        1);
    assertThat(released.allowed()).isFalse();
    assertThat(released.rejectionType()).isEqualTo("UNAVAILABLE");
  }

  @Test
  void longDetailResultUsesTheRealResultRefPathAndEnforcesTheCombinedVisibleBudget() {
    PracticeSubmissionHistoryRunScopeRegistry registry = new PracticeSubmissionHistoryRunScopeRegistry();
    PracticeSubmissionHistoryRunScopeRegistry.ScopeLease lease = registry.openScope(
        7L, "zh-CN", List.of(scopeInput("pp_history", "two-sum")));
    PracticeSubmissionHistoryRunScopeRegistry.ScopeUse signed = registry.reserveOverview(lease.scopeRef(), "pp_history");
    String submissionRef = signed.issueSubmissionRef(11L);
    RecordingRepository repository = new RecordingRepository();
    repository.detail = Optional.of(new PracticeSubmissionHistoryDetail(
        review(11L, "2026-08-13T11:42:00Z", true), "x".repeat(20_000)));
    ToolResultCompactionPolicy policy = new ToolResultCompactionPolicy(
        100, 100, 8_000, true, 60_000, 3, true, 120_000, 80, 2, 24, true, false);
    ReadPracticeSubmissionDetailAgentTool detailTool = new ReadPracticeSubmissionDetailAgentTool(
        registry, repository, TrustedProblemTagCatalog.empty(), policy, PracticeSubmissionHistoryToolMetrics.NOOP);
    JsonNode detail = detailTool.execute(arguments("submissionRef", submissionRef), context(lease.scopeRef(), true));
    InMemoryToolResultStore store = new InMemoryToolResultStore();
    ToolResultCompactor compactor = new ToolResultCompactor(new ObjectMapper(), policy, store);
    JsonNode visible = compactor.compactForModel(
        loopContext(lease.scopeRef()),
        1,
        new LlmToolCall(
            "detail-call",
            PracticeSubmissionHistoryToolContracts.READ_PRACTICE_SUBMISSION_DETAIL,
            JsonNodeFactory.instance.objectNode()),
        detail).visibleResult();
    ReadToolResultTool readTool = new ReadToolResultTool(
        store, policy, new PracticeSubmissionHistoryToolResultReadGuard(registry, PracticeSubmissionHistoryToolMetrics.NOOP));
    String resultRef = visible.path("resultRef").asText();

    assertThat(visible.path("type").asText()).isEqualTo("tool_result_preview");
    assertThat(readTool.execute(rangeArguments(resultRef, 0), context(lease.scopeRef(), true))
        .path("content").asText()).hasSize(8_000);
    assertThat(readTool.execute(rangeArguments(resultRef, 8_000), context(lease.scopeRef(), true))
        .path("content").asText()).hasSize(7_900);
    assertThat(readTool.execute(rangeArguments(resultRef, 15_900), context(lease.scopeRef(), true))
        .path("type").asText()).isEqualTo(PracticeSubmissionHistoryToolContracts.STATUS_BUDGET_EXHAUSTED);
  }

  private PracticeSubmissionHistoryScopeInput scopeInput(String problemRef, String slug) {
    return new PracticeSubmissionHistoryScopeInput(problemRef, slug, "Two Sum", List.of("Array", "Hash Table"));
  }

  private PracticeSubmissionHistoryOverview overview() {
    return new PracticeSubmissionHistoryOverview(3, 1, Instant.parse("2026-07-12T09:20:00Z"),
        review(11L, "2026-08-13T11:42:00Z", true));
  }

  private PracticeSubmissionHistoryReview review(long id, String submittedAt, boolean passed) {
    return new PracticeSubmissionHistoryReview(
        id,
        Instant.parse(submittedAt),
        "JAVA",
        new BigDecimal(passed ? "9.2" : "6.8"),
        passed,
        new BigDecimal("4.0"),
        new BigDecimal("2.0"),
        new BigDecimal("1.0"),
        new BigDecimal("0.5"),
        new BigDecimal("1.0"),
        passed ? List.of() : List.of("遗漏空前缀初始化"),
        passed ? List.of("补充边界条件说明") : List.of("初始化 frequency[0] = 1"),
        List.of(1L),
        "此前提交遗漏空前缀初始化；当前版本已通过 Review。");
  }

  private AgentExecutionContext context(String scopeRef, boolean codeDetailIntent) {
    return new AgentExecutionContext("run-history", 1, Map.of(
        PracticeSubmissionHistoryToolContracts.METADATA_SCOPE_REF, scopeRef,
        PracticeSubmissionHistoryToolContracts.METADATA_CODE_DETAIL_INTENT, codeDetailIntent), false);
  }

  private AgentLoopContext loopContext(String scopeRef) {
    Map<String, Object> metadata = Map.of(
        PracticeSubmissionHistoryToolContracts.METADATA_SCOPE_REF, scopeRef,
        PracticeSubmissionHistoryToolContracts.METADATA_CODE_DETAIL_INTENT, true);
    AgentRequest request = new AgentRequest(
        "run-history", "run-history", List.of(LlmMessage.user("review old code")), metadata);
    return new AgentLoopContext("run-history", request, 1, metadata);
  }

  private com.fasterxml.jackson.databind.node.ObjectNode rangeArguments(String resultRef, int offset) {
    return JsonNodeFactory.instance.objectNode()
        .put("resultRef", resultRef)
        .put("offset", offset)
        .put("limit", 8_000);
  }

  private JsonNode arguments(String... values) {
    com.fasterxml.jackson.databind.node.ObjectNode node = JsonNodeFactory.instance.objectNode();
    for (int index = 0; index < values.length; index += 2) {
      node.put(values[index], values[index + 1]);
    }
    return node;
  }

  private static final class RecordingRepository implements PracticeSubmissionHistoryToolRepository {

    private Optional<PracticeSubmissionHistoryOverview> overview = Optional.empty();
    private Optional<PracticeSubmissionHistoryDetail> detail = Optional.empty();
    private PracticeSubmissionHistoryPage firstPage = new PracticeSubmissionHistoryPage(List.of(), false);
    private PracticeSubmissionHistoryPage nextPage = new PracticeSubmissionHistoryPage(List.of(), false);
    private int overviewCalls;
    private int listCalls;
    private int detailCalls;
    private long lastUserId;
    private String lastProblemSlug;
    private Instant lastAfterCreatedAt;
    private Long lastAfterReviewId;
    private int lastLimit;

    @Override
    public Optional<PracticeSubmissionHistoryOverview> findOverview(long userId, String problemSlug) {
      overviewCalls++;
      lastUserId = userId;
      lastProblemSlug = problemSlug;
      return overview;
    }

    @Override
    public PracticeSubmissionHistoryPage findSubmissions(
        long userId,
        String problemSlug,
        Instant afterCreatedAt,
        Long afterReviewId,
        int limit
    ) {
      listCalls++;
      lastUserId = userId;
      lastProblemSlug = problemSlug;
      lastAfterCreatedAt = afterCreatedAt;
      lastAfterReviewId = afterReviewId;
      lastLimit = limit;
      return afterCreatedAt == null ? firstPage : nextPage;
    }

    @Override
    public Optional<PracticeSubmissionHistoryDetail> findSubmissionDetail(
        long userId,
        String problemSlug,
        long reviewId
    ) {
      detailCalls++;
      lastUserId = userId;
      lastProblemSlug = problemSlug;
      return detail;
    }
  }
}
