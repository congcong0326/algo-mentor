package org.congcong.algomentor.agent.persistence.postgres.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunFilter;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentAuditMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditRunDetailRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditRunRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditRunStatisticsRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditStepRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditToolCallRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditToolResultRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditTurnRow;
import org.junit.jupiter.api.Test;

class PostgresAgentAuditQueryTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void mapsPaginationAndAllListFilterFieldsWithoutLoadingLargeSnapshots() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    AgentAuditRunFilter filter = new AgentAuditRunFilter(
        Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-02T00:00:00Z"),
        2, 25, 7L, "PRACTICE_CHAT", "LEARNING", "practice", 11L, 13L, 17L, "openai", "gpt-test",
        "SUCCEEDED", "stop", true, false, true, false, 200L, 800L, 0.2D, 0.8D);
    when(mapper.findRuns(filter)).thenReturn(List.of(runRow()));
    when(mapper.countRuns(filter)).thenReturn(26L);
    when(mapper.findRunStatistics(filter)).thenReturn(new AgentAuditRunStatisticsRow(26L, 3L, 4L, 20L, 180_000L, 42_000L));

    var page = new PostgresAgentAuditQuery(mapper).findRuns(filter);

    assertThat(page.total()).isEqualTo(26L);
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.pageSize()).isEqualTo(25);
    assertThat(page.statistics())
        .extracting(statistics -> statistics.runCount(), statistics -> statistics.overBudgetRunCount(),
            statistics -> statistics.compactionRunCount(), statistics -> statistics.cacheRatio())
        .containsExactly(26L, 3L, 4L, 42_000D / 180_000D);
    assertThat(page.items()).singleElement().satisfies(run -> {
      assertThat(run.runId()).isEqualTo(17L);
      assertThat(run.taskId()).isEqualTo(11L);
      assertThat(run.turnId()).isEqualTo(13L);
      assertThat(run.finalRequestTokenEstimate()).isEqualTo(7_920);
      assertThat(run.actualInputTokens()).isEqualTo(8_762L);
      assertThat(run.cachedTokens()).isEqualTo(640L);
      assertThat(run.purpose()).isEqualTo("LEARNING");
      assertThat(run.source()).isEqualTo("practice");
    });
    verify(mapper).findRuns(filter);
    verify(mapper).countRuns(filter);
    verify(mapper).findRunStatistics(filter);
  }

  @Test
  void mapsStepsWithMissingSnapshotJsonAndAggregatesProviderUsageAcrossSteps() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    when(mapper.findRun(17L)).thenReturn(runDetailRow());
    when(mapper.findTaskTurns(11L)).thenReturn(List.of());
    when(mapper.findSteps(17L)).thenReturn(List.of(
        stepRow(1, usage(4000, 100, 300, 0, 4300), metadata(3900), true),
        stepRow(2, usage(5000, 200, 500, 50, 5550), null, false)));

    var detail = new PostgresAgentAuditQuery(mapper).findRun(17L).orElseThrow();

    assertThat(detail.steps()).hasSize(2);
    assertThat(detail.steps().get(0).messageCount()).isEqualTo(1);
    assertThat(detail.steps().get(0).roleCounts()).containsEntry("user", 1);
    assertThat(detail.steps().get(1).messageCount()).isNull();
    assertThat(detail.steps().get(1).snapshotAvailable()).isFalse();
    assertThat(detail.totalUsage())
        .extracting(usage -> usage.inputTokens(), usage -> usage.cachedTokens(), usage -> usage.outputTokens(),
            usage -> usage.reasoningTokens(), usage -> usage.totalTokens())
        .containsExactly(9000L, 300L, 800L, 50L, 9850L);
  }

  @Test
  void limitsToolResultReadsAndNeverExposesExpiredContent() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    AgentAuditToolResultRow expired = new AgentAuditToolResultRow(
        17L, 1, "call-1", "lookup", "SUCCEEDED", "blob", "tool-result:88", "hash", 24, 2,
        OBJECT_MAPPER.createObjectNode().put("value", "preview"), null, "sensitive body", true,
        Instant.now().minusSeconds(1), null);
    when(mapper.findToolResult(17L, "call-1", true, 12, 40)).thenReturn(expired);

    var result = new PostgresAgentAuditQuery(mapper)
        .findToolResult(17L, "call-1", true, 12, 40)
        .orElseThrow();

    assertThat(result.preview().get("value").asText()).isEqualTo("preview");
    assertThat(result.content()).isNull();
    assertThat(result.contentAvailable()).isFalse();
    assertThat(result.retentionActive()).isFalse();
    verify(mapper).findToolResult(17L, "call-1", true, 12, 40);
    assertThatThrownBy(() -> new PostgresAgentAuditQuery(mapper)
        .findToolResult(17L, "call-1", true, -1, 40))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Audit tool result read range is invalid");
  }

  @Test
  void doesNotLoadRawRequestSnapshotUntilExplicitlyRequested() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    AgentAuditStepRow row = stepRow(1, usage(4000, 100, 300, 0, 4300), metadata(3900), true);
    when(mapper.findStep(17L, 1, false)).thenReturn(row);

    var detail = new PostgresAgentAuditQuery(mapper).findStep(17L, 1, false).orElseThrow();

    assertThat(detail.requestSnapshot()).isNull();
    verify(mapper).findStep(17L, 1, false);
  }

  @Test
  void derivesProviderActualOverBudgetStatusWithoutMutatingStoredSnapshotMetadata() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    AgentAuditStepRow row = stepRow(1, usage(8_762, 640, 400, 0, 9_162), metadata(7_920), true);
    row = new AgentAuditStepRow(
        row.stepIndex(), row.status(), row.provider(), row.model(), row.finishReason(), row.startedAt(), row.endedAt(),
        row.usage(), row.error(), row.snapshotId(), row.promptTokenBudget(), row.messages(), row.tools(), row.toolChoice(),
        row.generationOptions(), OBJECT_MAPPER.createObjectNode().set("metadata", row.snapshotMetadata()), row.requestHash(),
        row.redactionPolicyVersion(), row.snapshotRetentionExpiresAt(), row.snapshotMetadata(), row.stepMetadata(), row.toolCallCount(),
        row.failedToolCallCount());
    when(mapper.findStep(17L, 1, false)).thenReturn(row);
    when(mapper.findToolCalls(17L, 1)).thenReturn(List.of());

    var detail = new PostgresAgentAuditQuery(mapper).findStep(17L, 1, false).orElseThrow();

    assertThat(detail.snapshotMetadata().get("budgetStatus").asText())
        .isEqualTo("PROVIDER_ACTUAL_OVER_BUDGET");
    assertThat(detail.snapshotMetadata().get("overBudgetTokens").asLong()).isEqualTo(762L);
    assertThat(detail.snapshotMetadata().get("actualInputTokens").asLong()).isEqualTo(8_762L);
    assertThat(detail.snapshotMetadata().get("cachedTokens").asLong()).isEqualTo(640L);
    assertThat(detail.requestSnapshot().get("metadata").get("budgetStatus").asText())
        .isEqualTo("PROVIDER_ACTUAL_OVER_BUDGET");
    assertThat(row.snapshotMetadata().get("budgetStatus")).isNull();
    assertThat(row.requestSnapshot().get("metadata").get("budgetStatus")).isNull();
    assertThat(row.snapshotMetadata().get("actualInputTokens")).isNull();
  }

  @Test
  void derivesProviderActualOverBudgetStatusForOlderSnapshotsWithoutMetadata() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    AgentAuditStepRow row = stepRow(1, usage(8_762, 640, 400, 0, 9_162), null, true);
    when(mapper.findStep(17L, 1, false)).thenReturn(row);
    when(mapper.findToolCalls(17L, 1)).thenReturn(List.of());

    var detail = new PostgresAgentAuditQuery(mapper).findStep(17L, 1, false).orElseThrow();

    assertThat(detail.snapshotMetadata().get("budgetStatus").asText())
        .isEqualTo("PROVIDER_ACTUAL_OVER_BUDGET");
    assertThat(detail.snapshotMetadata().get("overBudgetTokens").asLong()).isEqualTo(762L);
  }

  @Test
  void preservesMissingProviderUsageAsUnknownInsteadOfZero() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    when(mapper.findRun(17L)).thenReturn(runDetailRow());
    when(mapper.findTaskTurns(11L)).thenReturn(List.of());
    when(mapper.findSteps(17L)).thenReturn(List.of(
        stepRow(1, OBJECT_MAPPER.createObjectNode(), metadata(3_900), true)));

    var detail = new PostgresAgentAuditQuery(mapper).findRun(17L).orElseThrow();

    assertThat(detail.steps()).singleElement().satisfies(step ->
        assertThat(step.usage())
            .extracting(usage -> usage.inputTokens(), usage -> usage.cachedTokens(), usage -> usage.outputTokens(),
                usage -> usage.reasoningTokens(), usage -> usage.totalTokens())
            .containsOnlyNulls());
    assertThat(detail.totalUsage())
        .extracting(usage -> usage.inputTokens(), usage -> usage.cachedTokens(), usage -> usage.outputTokens(),
            usage -> usage.reasoningTokens(), usage -> usage.totalTokens())
        .containsOnlyNulls();
  }

  @Test
  void mapsFailedToolCallsWithoutSynthesizingAResult() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    AgentAuditStepRow step = stepRow(1, usage(4_000, 0, 0, 0, 4_000), metadata(3_900), true);
    AgentAuditToolCallRow failed = new AgentAuditToolCallRow(
        "call-1", "lookup", "FAILED", OBJECT_MAPPER.createObjectNode().put("query", "edge case"),
        null, null, null, null, null, null, 21, 6, null, null, null, 12L,
        OBJECT_MAPPER.createObjectNode().put("code", "TOOL_EXECUTION_FAILED").put("message", "lookup failed"),
        "v1", Instant.parse("2026-08-01T12:00:00Z"), Instant.parse("2026-08-01T12:00:01Z"));
    when(mapper.findStep(17L, 1, false)).thenReturn(step);
    when(mapper.findToolCalls(17L, 1)).thenReturn(List.of(failed));

    var detail = new PostgresAgentAuditQuery(mapper).findStep(17L, 1, false).orElseThrow();

    assertThat(detail.toolCalls()).singleElement().satisfies(tool -> {
      assertThat(tool.status()).isEqualTo("FAILED");
      assertThat(tool.result()).isNull();
      assertThat(tool.preview()).isNull();
      assertThat(tool.errorCode()).isEqualTo("TOOL_EXECUTION_FAILED");
      assertThat(tool.errorMessage()).isEqualTo("lookup failed");
    });
  }

  @Test
  void keepsRetryAttemptsWithTheirCurrentTurn() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    var attempts = OBJECT_MAPPER.createArrayNode()
        .add(OBJECT_MAPPER.createObjectNode().put("runId", 16).put("attemptNo", 1).put("status", "FAILED"))
        .add(OBJECT_MAPPER.createObjectNode().put("runId", 17).put("attemptNo", 2).put("status", "SUCCEEDED"));
    when(mapper.findRun(17L)).thenReturn(runDetailRow());
    when(mapper.findTaskTurns(11L)).thenReturn(List.of(new AgentAuditTurnRow(
        13L, 4L, "SUCCEEDED", "question", Instant.parse("2026-08-01T12:00:00Z"), "answer",
        Instant.parse("2026-08-01T12:00:01Z"), 2, attempts, true, 8_762L, 640L, 400L, null, 9_162L, 762L)));
    when(mapper.findSteps(17L)).thenReturn(List.of());

    var detail = new PostgresAgentAuditQuery(mapper).findRun(17L).orElseThrow();

    assertThat(detail.currentTurn()).isNotNull();
    assertThat(detail.currentTurn().runAttempts())
        .extracting(attempt -> attempt.runId(), attempt -> attempt.attemptNo(), attempt -> attempt.status())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(16L, 1, "FAILED"),
            org.assertj.core.groups.Tuple.tuple(17L, 2, "SUCCEEDED"));
  }

  @Test
  void retainsBlobContentOnlyWhenRetentionIsActive() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    AgentAuditToolResultRow active = new AgentAuditToolResultRow(
        17L, 1, "call-1", "lookup", "SUCCEEDED", "blob", "tool-result:88", "hash", 24, 2,
        null, OBJECT_MAPPER.createObjectNode().put("preview", "safe"), "requested range", true,
        Instant.now().plusSeconds(60), null);
    when(mapper.findToolResult(17L, "call-1", true, 0, 20)).thenReturn(active);

    var result = new PostgresAgentAuditQuery(mapper)
        .findToolResult(17L, "call-1", true, 0, 20)
        .orElseThrow();

    assertThat(result.content()).isEqualTo("requested range");
    assertThat(result.preview().get("preview").asText()).isEqualTo("safe");
    assertThat(result.contentAvailable()).isTrue();
    assertThat(result.retentionActive()).isTrue();
  }

  @Test
  void boundsOnDemandHistoricalInlineResultToAnAuditPreview() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    String sensitiveTail = "sensitive-tail-value";
    var inlineResult = OBJECT_MAPPER.createObjectNode().put("content", "x".repeat(4_100) + sensitiveTail);
    AgentAuditToolResultRow row = new AgentAuditToolResultRow(
        17L, 1, "call-1", "lookup", "SUCCEEDED", "inline", null, "hash", inlineResult.toString().length(), 1,
        inlineResult, null, null, false, Instant.now().plusSeconds(60), null);
    when(mapper.findToolResult(17L, "call-1", false, 0, 20)).thenReturn(row);

    var result = new PostgresAgentAuditQuery(mapper)
        .findToolResult(17L, "call-1", false, 0, 20)
        .orElseThrow();

    assertThat(result.preview().path("type").asText()).isEqualTo("audit_tool_result_preview");
    assertThat(result.preview().path("preview").asText()).hasSize(4_000).doesNotContain(sensitiveTail);
    assertThat(result.content()).isNull();
  }

  @Test
  void boundsLargeHistoricalInlineToolResultsToAnAuditPreview() {
    AgentAuditMapper mapper = mock(AgentAuditMapper.class);
    AgentAuditStepRow row = stepRow(1, usage(4000, 100, 300, 0, 4300), metadata(3900), true);
    String sensitiveTail = "sensitive-tail-value";
    var inlineResult = OBJECT_MAPPER.createObjectNode().put("content", "x".repeat(4_100) + sensitiveTail);
    var toolCall = new org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditToolCallRow(
        "call-1", "lookup", "SUCCEEDED", null, inlineResult, null, "inline", null, null, "hash",
        0, 0, inlineResult.toString().length(), 1_100, 1, 12L, null, "v1",
        Instant.parse("2026-08-01T12:00:00Z"), Instant.parse("2026-08-01T12:00:01Z"));
    when(mapper.findStep(17L, 1, false)).thenReturn(row);
    when(mapper.findToolCalls(17L, 1)).thenReturn(List.of(toolCall));

    var detail = new PostgresAgentAuditQuery(mapper).findStep(17L, 1, false).orElseThrow();
    JsonNode displayedResult = detail.toolCalls().get(0).result();

    assertThat(displayedResult.path("type").asText()).isEqualTo("audit_tool_result_preview");
    assertThat(displayedResult.path("truncated").asBoolean()).isTrue();
    assertThat(displayedResult.path("preview").asText()).hasSize(4_000).doesNotContain(sensitiveTail);
    assertThat(displayedResult.path("charCount").asInt()).isGreaterThan(4_000);
  }

  private AgentAuditRunRow runRow() {
    return new AgentAuditRunRow(
        17L, "run-17", 11L, 13L, 7L, "PRACTICE_CHAT", "LEARNING", "practice", "openai", "gpt-test", "SUCCEEDED", "stop",
        2, 0, 1, 0, 8_000, 6_800, 7_920, 8_762L, 640L, 762L, false, 0, false,
        Instant.parse("2026-08-01T12:00:00Z"), Instant.parse("2026-08-01T12:00:10Z"));
  }

  private AgentAuditRunDetailRow runDetailRow() {
    AgentAuditRunRow row = runRow();
    return new AgentAuditRunDetailRow(
        row.runId(), row.runUuid(), row.taskId(), row.turnId(), row.userId(), row.scenario(), row.purpose(), row.source(), row.provider(),
        row.model(), row.status(), row.finishReason(), row.stepCount(), row.failedStepCount(), row.toolCallCount(),
        row.failedToolCallCount(), row.promptTokenBudget(), row.assemblyTokenEstimate(),
        row.finalRequestTokenEstimate(), row.actualInputTokens(), row.cachedTokens(), row.overBudgetTokens(),
        row.compactionApplied(), row.compactionActionCount(), row.providerError(), row.startedAt(), row.endedAt(),
        1, null, 4, null, Instant.parse("2026-09-01T00:00:00Z"), null);
  }

  private AgentAuditStepRow stepRow(
      int index,
      JsonNode usage,
      JsonNode metadata,
      boolean withSnapshot
  ) {
    JsonNode messages = withSnapshot ? OBJECT_MAPPER.createArrayNode().add(
        OBJECT_MAPPER.createObjectNode().put("role", "user").put("content", "question")) : null;
    return new AgentAuditStepRow(
        index, "SUCCEEDED", "openai", "gpt-test", "stop", Instant.parse("2026-08-01T12:00:00Z"),
        Instant.parse("2026-08-01T12:00:01Z"), usage, null, withSnapshot ? 100L + index : null,
        withSnapshot ? 8_000 : null, messages, null, null, null, null, "hash", "v1",
        null, metadata, null, 0, 0);
  }

  private JsonNode usage(long input, long cached, long output, long reasoning, long total) {
    return OBJECT_MAPPER.createObjectNode()
        .put("inputTokens", input)
        .put("cachedTokens", cached)
        .put("outputTokens", output)
        .put("reasoningTokens", reasoning)
        .put("totalTokens", total);
  }

  private JsonNode metadata(int finalEstimate) {
    return OBJECT_MAPPER.createObjectNode()
        .put("messageTokenEstimate", 3_800)
        .put("toolsTokenEstimate", 50)
        .put("providerOverheadTokenEstimate", 50)
        .put("finalRequestTokenEstimate", finalEstimate)
        .put("compactionApplied", false);
  }
}
