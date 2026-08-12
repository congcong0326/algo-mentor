package org.congcong.algomentor.api.controller.admin.ai.model;

import java.time.Instant;
import java.util.List;

/** 当前 task 的 turn 摘要，正文按服务端范围限制。 */
public record AdminAiAuditTurnResponse(
    long turnId,
    long sequenceNo,
    String status,
    String userMessage,
    Instant userMessageAt,
    String assistantMessage,
    Instant assistantMessageAt,
    int runAttemptCount,
    List<AdminAiAuditRunAttemptResponse> runAttempts,
    boolean hasTools,
    AdminAiAuditUsageResponse usage,
    Long overBudgetTokens
) {
}
