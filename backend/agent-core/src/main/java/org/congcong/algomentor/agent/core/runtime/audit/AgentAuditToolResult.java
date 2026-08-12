package org.congcong.algomentor.agent.core.runtime.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 按需读取的工具结果；content 仅在留存有效且请求明确要求时返回。 */
public record AgentAuditToolResult(
    long runId,
    int stepIndex,
    String toolCallId,
    String toolName,
    String status,
    String storageMode,
    String resultRef,
    String sha256,
    Integer charCount,
    Integer lineCount,
    JsonNode preview,
    String content,
    boolean contentAvailable,
    boolean retentionActive,
    Instant retentionExpiresAt
) {
}
