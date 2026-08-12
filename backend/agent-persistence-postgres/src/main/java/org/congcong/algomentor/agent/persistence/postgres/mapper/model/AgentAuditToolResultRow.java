package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 工具结果的延迟读取行，content 由 SQL 在明确请求时做范围截取。 */
public record AgentAuditToolResultRow(
    long runId,
    int stepIndex,
    String toolCallId,
    String toolName,
    String status,
    String resultStorageMode,
    String resultRef,
    String resultSha256,
    Integer resultCharCount,
    Integer resultLineCount,
    JsonNode result,
    JsonNode preview,
    String content,
    boolean contentAvailable,
    Instant diagnosticRetentionExpiresAt,
    Instant diagnosticRedactedAt
) {
}
