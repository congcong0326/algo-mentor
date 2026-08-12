package org.congcong.algomentor.api.controller.admin.ai;

import java.util.List;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunDetail;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunAttempt;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunPage;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunSummary;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditRunStatistics;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditStepDetail;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditStepSummary;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditToolCall;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditToolResult;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditTurnSummary;
import org.congcong.algomentor.agent.core.runtime.audit.AgentAuditUsage;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditRunDetailResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditRunAttemptResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditRunPageResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditRunResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditRunStatisticsResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditStepDetailResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditStepResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditToolCallResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditToolResultResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditTurnResponse;
import org.congcong.algomentor.api.controller.admin.ai.model.AdminAiAuditUsageResponse;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;

/** 审计查询领域模型到管理员 HTTP 契约的映射，不向列表注入大 JSON。 */
final class AdminAiAuditResponseMapper {

  private final IdentityUserRepository identityUserRepository;

  AdminAiAuditResponseMapper(IdentityUserRepository identityUserRepository) {
    this.identityUserRepository = identityUserRepository;
  }

  AdminAiAuditRunPageResponse page(AgentAuditRunPage page) {
    return new AdminAiAuditRunPageResponse(
        page.items().stream().map(this::run).toList(), page.total(), page.page(), page.pageSize(),
        statistics(page.statistics()));
  }

  AdminAiAuditRunDetailResponse detail(AgentAuditRunDetail detail) {
    return new AdminAiAuditRunDetailResponse(
        run(detail.summary()),
        detail.attemptNo(),
        detail.retryOfRunId(),
        detail.maxSteps(),
        detail.errorCode(),
        detail.errorMessage(),
        detail.diagnosticRetentionExpiresAt(),
        detail.diagnosticRedactedAt(),
        detail.currentTurn() == null ? null : turn(detail.currentTurn()),
        detail.taskTurns().stream().map(this::turn).toList(),
        detail.steps().stream().map(this::step).toList(),
        usage(detail.totalUsage()));
  }

  AdminAiAuditStepDetailResponse stepDetail(AgentAuditStepDetail detail) {
    return new AdminAiAuditStepDetailResponse(
        step(detail.summary()),
        detail.snapshotId(),
        detail.requestSnapshot(),
        detail.messages(),
        detail.tools(),
        detail.toolChoice(),
        detail.generationOptions(),
        detail.requestHash(),
        detail.redactionPolicyVersion(),
        detail.snapshotRetentionExpiresAt(),
        detail.snapshotMetadata(),
        detail.toolCalls().stream().map(this::toolCall).toList());
  }

  AdminAiAuditToolResultResponse toolResult(AgentAuditToolResult result) {
    return new AdminAiAuditToolResultResponse(
        result.runId(), result.stepIndex(), result.toolCallId(), result.toolName(), result.status(),
        result.storageMode(), result.resultRef(), result.sha256(), result.charCount(), result.lineCount(),
        result.preview(), result.content(), result.contentAvailable(), result.retentionActive(), result.retentionExpiresAt());
  }

  private AdminAiAuditRunResponse run(AgentAuditRunSummary run) {
    return new AdminAiAuditRunResponse(
        run.runId(), run.runUuid(), run.taskId(), run.turnId(), run.userId(), displayName(run.userId()), run.scenario(),
        run.purpose(), run.source(),
        run.provider(), run.model(), run.status(), run.finishReason(), run.stepCount(), run.failedStepCount(),
        run.toolCallCount(), run.failedToolCallCount(), run.promptTokenBudget(), run.assemblyTokenEstimate(),
        run.finalRequestTokenEstimate(), run.actualInputTokens(), run.cachedTokens(), run.overBudgetTokens(),
        run.compactionApplied(), run.compactionActionCount(), run.providerError(), run.startedAt(), run.endedAt());
  }

  private AdminAiAuditRunStatisticsResponse statistics(AgentAuditRunStatistics statistics) {
    return new AdminAiAuditRunStatisticsResponse(
        statistics.runCount(), statistics.overBudgetRunCount(), statistics.overBudgetRate(),
        statistics.compactionRunCount(), statistics.compactionRate(), statistics.usageReportedRunCount(),
        statistics.inputTokens(), statistics.cachedTokens(), statistics.cacheRatio());
  }

  private AdminAiAuditTurnResponse turn(AgentAuditTurnSummary turn) {
    return new AdminAiAuditTurnResponse(
        turn.turnId(), turn.sequenceNo(), turn.status(), turn.userMessage(), turn.userMessageAt(),
        turn.assistantMessage(), turn.assistantMessageAt(), turn.runAttemptCount(),
        turn.runAttempts().stream().map(this::runAttempt).toList(), turn.hasTools(), usage(turn.usage()),
        turn.overBudgetTokens());
  }

  private AdminAiAuditRunAttemptResponse runAttempt(AgentAuditRunAttempt attempt) {
    return new AdminAiAuditRunAttemptResponse(attempt.runId(), attempt.attemptNo(), attempt.status());
  }

  private AdminAiAuditStepResponse step(AgentAuditStepSummary step) {
    return new AdminAiAuditStepResponse(
        step.stepIndex(), step.status(), step.provider(), step.model(), step.finishReason(), step.startedAt(), step.endedAt(),
        step.messageCount(), step.roleCounts(), step.messageTokenEstimate(), step.toolsCount(), step.toolsTokenEstimate(),
        step.providerOverheadTokenEstimate(), step.finalRequestTokenEstimate(), step.promptTokenBudget(),
        step.remainingBudgetTokens(), usage(step.usage()), step.compactionApplied(), step.compactionMetadata(),
        step.snapshotAvailable(), step.toolCallCount(), step.failedToolCallCount(), step.errorCode(), step.errorMessage());
  }

  private AdminAiAuditToolCallResponse toolCall(AgentAuditToolCall toolCall) {
    return new AdminAiAuditToolCallResponse(
        toolCall.toolCallId(), toolCall.toolName(), toolCall.status(), toolCall.arguments(), toolCall.result(),
        toolCall.preview(), toolCall.resultStorageMode(), toolCall.resultRef(), toolCall.resultSha256(),
        toolCall.argumentCharCount(), toolCall.argumentTokenEstimate(), toolCall.resultCharCount(),
        toolCall.resultTokenEstimate(), toolCall.resultLineCount(), toolCall.durationMillis(), toolCall.errorCode(),
        toolCall.errorMessage(), toolCall.redactionPolicyVersion(), toolCall.startedAt(), toolCall.endedAt());
  }

  private AdminAiAuditUsageResponse usage(AgentAuditUsage usage) {
    if (usage == null) {
      return new AdminAiAuditUsageResponse(null, null, null, null, null, null, null);
    }
    Long uncached = usage.inputTokens() == null || usage.cachedTokens() == null
        ? null
        : Math.max(0, usage.inputTokens() - usage.cachedTokens());
    Double ratio = usage.inputTokens() == null || usage.inputTokens() == 0 || usage.cachedTokens() == null
        ? null
        : (double) usage.cachedTokens() / usage.inputTokens();
    return new AdminAiAuditUsageResponse(
        usage.inputTokens(), usage.cachedTokens(), uncached, usage.outputTokens(), usage.reasoningTokens(),
        usage.totalTokens(), ratio);
  }

  private String displayName(Long userId) {
    return userId == null ? null : identityUserRepository.findUserById(userId)
        .map(user -> user.displayName())
        .orElse(null);
  }
}
