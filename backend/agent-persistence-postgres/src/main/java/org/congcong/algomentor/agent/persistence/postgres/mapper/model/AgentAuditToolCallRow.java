package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 工具调用审计字段；所有 JSON 均已在写入阶段脱敏。 */
public record AgentAuditToolCallRow(
    String toolCallId,
    String toolName,
    String status,
    JsonNode arguments,
    JsonNode result,
    JsonNode preview,
    String resultStorageMode,
    Long resultBlobId,
    String resultRef,
    String resultSha256,
    Integer argumentCharCount,
    Integer argumentTokenEstimate,
    Integer resultCharCount,
    Integer resultTokenEstimate,
    Integer resultLineCount,
    Long durationMillis,
    JsonNode error,
    String redactionPolicyVersion,
    Instant startedAt,
    Instant endedAt
) {
}
