package org.congcong.algomentor.agent.persistence.postgres.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditQuery;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunDetail;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunFilter;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunPage;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunSummary;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunStatistics;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunAttempt;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditStepDetail;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditStepSummary;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditToolCall;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditToolResult;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditTurnSummary;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditUsage;
import org.congcong.algomentor.agent.core.runtime.model.AgentTraceJsonKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultJsonKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultTypes;
import org.congcong.algomentor.agent.persistence.postgres.mapper.AgentAuditMapper;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditRunDetailRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditRunRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditRunStatisticsRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditStepRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditToolCallRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditToolResultRow;
import org.congcong.algomentor.agent.persistence.postgres.mapper.model.AgentAuditTurnRow;

/** PostgreSQL 运行时数据到只读审计领域模型的映射边界。 */
public class PostgresAgentAuditQuery implements AgentAuditQuery {

  private static final int MAX_TOOL_RESULT_READ = 16_000;
  private static final int MAX_INLINE_TOOL_RESULT_PREVIEW_CHARS = 4_000;

  private final AgentAuditMapper mapper;

  public PostgresAgentAuditQuery(AgentAuditMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public AgentAuditRunPage findRuns(AgentAuditRunFilter filter) {
    if (filter == null) {
      throw new IllegalArgumentException("Audit run filter must not be null");
    }
    List<AgentAuditRunSummary> items = mapper.findRuns(filter).stream().map(this::summary).toList();
    AgentAuditRunStatisticsRow statistics = mapper.findRunStatistics(filter);
    return new AgentAuditRunPage(items, mapper.countRuns(filter), filter.page(), filter.pageSize(), statistics(statistics));
  }

  @Override
  public Optional<AgentAuditRunDetail> findRun(long runId) {
    requirePositive(runId, "Audit run id");
    AgentAuditRunDetailRow row = mapper.findRun(runId);
    if (row == null) {
      return Optional.empty();
    }
    List<AgentAuditStepSummary> steps = mapper.findSteps(runId).stream().map(this::stepSummary).toList();
    List<AgentAuditTurnSummary> turns = mapper.findTaskTurns(row.taskId()).stream().map(this::turn).toList();
    AgentAuditTurnSummary currentTurn = turns.stream()
        .filter(turn -> turn.turnId() == row.turnId())
        .findFirst()
        .orElse(null);
    return Optional.of(new AgentAuditRunDetail(
        summary(row),
        row.attemptNo(),
        row.retryOfRunId(),
        row.maxSteps(),
        text(row.error(), "code"),
        text(row.error(), "message"),
        row.diagnosticRetentionExpiresAt(),
        row.diagnosticRedactedAt(),
        currentTurn,
        turns,
        steps,
        totalUsage(steps)));
  }

  @Override
  public Optional<AgentAuditStepDetail> findStep(long runId, int stepIndex, boolean includeRequestSnapshot) {
    requirePositive(runId, "Audit run id");
    if (stepIndex < 1) {
      throw new IllegalArgumentException("Audit step index must be positive");
    }
    AgentAuditStepRow row = mapper.findStep(runId, stepIndex, includeRequestSnapshot);
    if (row == null) {
      return Optional.empty();
    }
    JsonNode metadata = auditMetadataForRead(row);
    JsonNode requestSnapshot = requestSnapshotForRead(row.requestSnapshot(), metadata);
    List<AgentAuditToolCall> toolCalls = mapper.findToolCalls(runId, stepIndex).stream()
        .map(this::toolCall)
        .toList();
    return Optional.of(new AgentAuditStepDetail(
        stepSummary(row),
        row.snapshotId(),
        requestSnapshot,
        row.messages(),
        row.tools(),
        row.toolChoice(),
        row.generationOptions(),
        row.requestHash(),
        row.redactionPolicyVersion(),
        row.snapshotRetentionExpiresAt(),
        metadata,
        toolCalls));
  }

  @Override
  public Optional<AgentAuditToolResult> findToolResult(
      long runId,
      String toolCallId,
      boolean includeContent,
      int offset,
      int limit
  ) {
    requirePositive(runId, "Audit run id");
    if (toolCallId == null || toolCallId.isBlank()) {
      throw new IllegalArgumentException("Audit tool call id must not be blank");
    }
    if (offset < 0 || limit < 1 || limit > MAX_TOOL_RESULT_READ) {
      throw new IllegalArgumentException("Audit tool result read range is invalid");
    }
    AgentAuditToolResultRow row = mapper.findToolResult(runId, toolCallId, includeContent, offset, limit);
    if (row == null) {
      return Optional.empty();
    }
    boolean retentionActive = row.diagnosticRedactedAt() == null
        && (row.diagnosticRetentionExpiresAt() == null || row.diagnosticRetentionExpiresAt().isAfter(Instant.now()));
    return Optional.of(new AgentAuditToolResult(
        row.runId(),
        row.stepIndex(),
        row.toolCallId(),
        row.toolName(),
        row.status(),
        row.resultStorageMode(),
        row.resultRef(),
        row.resultSha256(),
        row.resultCharCount(),
        row.resultLineCount(),
        boundedToolResult(row.preview() == null ? row.result() : row.preview(), row.resultCharCount(), row.resultRef()),
        includeContent && retentionActive ? row.content() : null,
        row.contentAvailable() && retentionActive,
        retentionActive,
        row.diagnosticRetentionExpiresAt()));
  }

  private AgentAuditRunSummary summary(AgentAuditRunRow row) {
    return new AgentAuditRunSummary(
        row.runId(), row.runUuid(), row.taskId(), row.turnId(), row.userId(), row.scenario(), row.purpose(),
        row.source(), row.provider(),
        row.model(), row.status(), row.finishReason(), row.stepCount(), row.failedStepCount(), row.toolCallCount(),
        row.failedToolCallCount(), row.promptTokenBudget(), row.assemblyTokenEstimate(),
        row.finalRequestTokenEstimate(), row.actualInputTokens(), row.cachedTokens(), row.overBudgetTokens(),
        row.compactionApplied(), row.compactionActionCount(), row.providerError(), row.startedAt(), row.endedAt());
  }

  private AgentAuditRunStatistics statistics(AgentAuditRunStatisticsRow row) {
    if (row == null) {
      return new AgentAuditRunStatistics(0, 0, 0, 0, null, null);
    }
    return new AgentAuditRunStatistics(
        row.runCount(), row.overBudgetRunCount(), row.compactionRunCount(), row.usageReportedRunCount(),
        row.inputTokens(), row.cachedTokens());
  }

  private AgentAuditRunSummary summary(AgentAuditRunDetailRow row) {
    return new AgentAuditRunSummary(
        row.runId(), row.runUuid(), row.taskId(), row.turnId(), row.userId(), row.scenario(), row.purpose(),
        row.source(), row.provider(),
        row.model(), row.status(), row.finishReason(), row.stepCount(), row.failedStepCount(), row.toolCallCount(),
        row.failedToolCallCount(), row.promptTokenBudget(), row.assemblyTokenEstimate(),
        row.finalRequestTokenEstimate(), row.actualInputTokens(), row.cachedTokens(), row.overBudgetTokens(),
        row.compactionApplied(), row.compactionActionCount(), row.providerError(), row.startedAt(), row.endedAt());
  }

  private AgentAuditTurnSummary turn(AgentAuditTurnRow row) {
    return new AgentAuditTurnSummary(
        row.turnId(), row.sequenceNo(), row.status(), row.userMessage(), row.userMessageAt(), row.assistantMessage(),
        row.assistantMessageAt(), row.runAttemptCount(), runAttempts(row.runAttempts()), row.hasTools(),
        new AgentAuditUsage(row.inputTokens(), row.cachedTokens(), row.outputTokens(), row.reasoningTokens(), row.totalTokens()),
        row.overBudgetTokens());
  }

  private List<AgentAuditRunAttempt> runAttempts(JsonNode value) {
    if (value == null || !value.isArray()) {
      return List.of();
    }
    java.util.ArrayList<AgentAuditRunAttempt> attempts = new java.util.ArrayList<>();
    for (JsonNode item : value) {
      Long runId = longValue(item, "runId");
      Integer attemptNo = integer(item, "attemptNo");
      if (runId != null && attemptNo != null) {
        attempts.add(new AgentAuditRunAttempt(runId, attemptNo, text(item, "status")));
      }
    }
    return List.copyOf(attempts);
  }

  private AgentAuditStepSummary stepSummary(AgentAuditStepRow row) {
    JsonNode metadata = auditMetadataForRead(row);
    Integer messageCount = countOrMetadata(row.messages(), metadata, "messageCount");
    Integer toolsCount = countOrMetadata(row.tools(), metadata, "toolsCount");
    Map<String, Integer> roleCounts = roleCounts(row.messages());
    Integer finalEstimate = integer(metadata, "finalRequestTokenEstimate");
    Integer budget = row.promptTokenBudget();
    return new AgentAuditStepSummary(
        row.stepIndex(), row.status(), row.provider(), row.model(), row.finishReason(), row.startedAt(), row.endedAt(),
        messageCount, roleCounts,
        integer(metadata, "messageTokenEstimate"),
        toolsCount,
        integer(metadata, "toolsTokenEstimate"),
        integer(metadata, "providerOverheadTokenEstimate"),
        finalEstimate,
        budget,
        budget == null || finalEstimate == null ? null : budget - finalEstimate,
        usage(row.usage()),
        bool(metadata, "compactionApplied"),
        compactionMetadata(metadata),
        row.snapshotId() != null,
        row.toolCallCount(), row.failedToolCallCount(), text(row.error(), "code"), text(row.error(), "message"));
  }

  private AgentAuditToolCall toolCall(AgentAuditToolCallRow row) {
    return new AgentAuditToolCall(
        row.toolCallId(), row.toolName(), row.status(), row.arguments(),
        boundedToolResult(row.result(), row.resultCharCount(), row.resultRef()),
        boundedToolResult(row.preview(), null, row.resultRef()),
        row.resultStorageMode(), row.resultBlobId(), row.resultRef(), row.resultSha256(), row.argumentCharCount(),
        row.argumentTokenEstimate(), row.resultCharCount(), row.resultTokenEstimate(), row.resultLineCount(),
        row.durationMillis(), text(row.error(), "code"), text(row.error(), "message"), row.redactionPolicyVersion(),
        row.startedAt(), row.endedAt());
  }

  /**
   * 历史 trace 可能将大结果直接写入 result_json。审计读取只保留有界 preview，完整内容只能走受留存
   * 与范围限制保护的 blob 读取接口；该转换不会写回历史记录。
   */
  private JsonNode boundedToolResult(JsonNode value, Integer declaredCharCount, String resultRef) {
    if (value == null) {
      return null;
    }
    String serialized = value.toString();
    if (serialized.length() <= MAX_INLINE_TOOL_RESULT_PREVIEW_CHARS) {
      return value;
    }
    ObjectNode preview = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    preview.put(AgentToolResultJsonKeys.TYPE, AgentToolResultTypes.AUDIT_PREVIEW);
    preview.put(AgentToolResultJsonKeys.PREVIEW, serialized.substring(0, MAX_INLINE_TOOL_RESULT_PREVIEW_CHARS));
    preview.put(AgentToolResultJsonKeys.CHAR_COUNT,
        declaredCharCount == null ? serialized.length() : declaredCharCount);
    preview.put(AgentToolResultJsonKeys.TRUNCATED, true);
    if (resultRef != null && !resultRef.isBlank()) {
      preview.put(AgentToolResultJsonKeys.RESULT_REF, resultRef);
    }
    return preview;
  }

  private AgentAuditUsage totalUsage(List<AgentAuditStepSummary> steps) {
    if (steps.isEmpty()) {
      return AgentAuditUsage.empty();
    }
    return new AgentAuditUsage(
        sum(steps, usage -> usage.inputTokens()),
        sum(steps, usage -> usage.cachedTokens()),
        sum(steps, usage -> usage.outputTokens()),
        sum(steps, usage -> usage.reasoningTokens()),
        sum(steps, usage -> usage.totalTokens()));
  }

  private Long sum(List<AgentAuditStepSummary> steps, java.util.function.Function<AgentAuditUsage, Long> value) {
    long total = 0;
    boolean any = false;
    for (AgentAuditStepSummary step : steps) {
      Long item = value.apply(step.usage());
      if (item != null) {
        total += item;
        any = true;
      }
    }
    return any ? total : null;
  }

  private AgentAuditUsage usage(JsonNode value) {
    return new AgentAuditUsage(
        longValue(value, "inputTokens"), longValue(value, "cachedTokens"), longValue(value, "outputTokens"),
        longValue(value, "reasoningTokens"), longValue(value, "totalTokens"));
  }

  private Map<String, Integer> roleCounts(JsonNode messages) {
    if (messages == null || !messages.isArray()) {
      return Map.of();
    }
    Map<String, Integer> counts = new LinkedHashMap<>();
    messages.forEach(message -> {
      String role = text(message, "role");
      if (role != null) {
        counts.merge(role, 1, Integer::sum);
      }
    });
    return Map.copyOf(counts);
  }

  private JsonNode compactionMetadata(JsonNode metadata) {
    if (metadata == null || !metadata.isObject()) {
      return null;
    }
    com.fasterxml.jackson.databind.node.ObjectNode selected =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    List<String> names = List.of(
        "compactionApplied", "compactionPolicyVersion", "compactionActions", "compactionBeforeChars",
        "compactionAfterChars", "compactionBeforeTokenEstimate", "compactionAfterTokenEstimate",
        "snippedMessageGroupCount", "toolResultPreviewCount", "toolResultCompactedCount",
        "promptTruncatedSections", "truncatedSectionIds", "droppedSectionIds", "promptSectionActions");
    names.forEach(name -> {
      JsonNode value = metadata.get(name);
      if (value != null) {
        selected.set(name, value);
      }
    });
    return selected.isEmpty() ? null : selected;
  }

  /**
   * provider usage 在请求快照入库后才可得；读取时补充其派生的审计状态，不能回写历史快照。
   */
  private JsonNode auditMetadataForRead(AgentAuditStepRow row) {
    JsonNode source = row.snapshotMetadata();
    if (source == null && row.snapshotId() == null) {
      return null;
    }
    if (source != null && !source.isObject()) {
      return source;
    }
    ObjectNode metadata = source instanceof ObjectNode object
        ? object.deepCopy()
        : com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    copyStoredUsageMetadata(row.stepMetadata(), metadata);
    Long actualInputTokens = longValue(row.usage(), "inputTokens");
    Long cachedTokens = longValue(row.usage(), "cachedTokens");
    if (actualInputTokens != null) {
      metadata.put("actualInputTokens", actualInputTokens);
    }
    if (cachedTokens != null) {
      metadata.put("cachedTokens", cachedTokens);
    }

    Integer budget = row.promptTokenBudget();
    if (budget != null && budget > 0 && actualInputTokens != null && actualInputTokens > budget) {
      metadata.put("budgetStatus", "PROVIDER_ACTUAL_OVER_BUDGET");
      metadata.put("overBudgetTokens", actualInputTokens - budget);
    }
    return metadata;
  }

  private void copyStoredUsageMetadata(JsonNode source, ObjectNode target) {
    if (source == null || !source.isObject()) {
      return;
    }
    List<String> keys = List.of(
        "actualInputTokens", "cachedTokens", "outputTokens", "reasoningTokens", "totalTokens",
        "budgetStatus", "overBudgetTokens");
    for (String key : keys) {
      JsonNode value = source.get(key);
      if (value != null) {
        target.set(key, value);
      }
    }
  }

  private JsonNode requestSnapshotForRead(JsonNode source, JsonNode metadata) {
    if (source == null || !source.isObject() || metadata == null) {
      return source;
    }
    ObjectNode snapshot = ((ObjectNode) source).deepCopy();
    snapshot.set(AgentTraceJsonKeys.METADATA, metadata);
    return snapshot;
  }

  private Integer integer(JsonNode node, String key) {
    Long value = longValue(node, key);
    return value == null || value > Integer.MAX_VALUE ? null : value.intValue();
  }

  private Integer countOrMetadata(JsonNode value, JsonNode metadata, String metadataKey) {
    if (value != null && value.isArray()) {
      return value.size();
    }
    return integer(metadata, metadataKey);
  }

  private Long longValue(JsonNode node, String key) {
    JsonNode value = node == null ? null : node.get(key);
    return value != null && value.canConvertToLong() && !value.isTextual() ? value.longValue() : null;
  }

  private boolean bool(JsonNode node, String key) {
    JsonNode value = node == null ? null : node.get(key);
    return value != null && value.asBoolean(false);
  }

  private String text(JsonNode node, String key) {
    JsonNode value = node == null ? null : node.get(key);
    return value != null && value.isTextual() ? value.asText() : null;
  }

  private void requirePositive(long value, String field) {
    if (value < 1) {
      throw new IllegalArgumentException(field + " must be positive");
    }
  }
}
