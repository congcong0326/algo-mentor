package org.congcong.algomentor.api.controller.admin.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 脱敏工具调用及结果 preview。 */
public record AdminAiAuditToolCallResponse(
    String toolCallId,
    String toolName,
    String status,
    JsonNode arguments,
    JsonNode result,
    JsonNode preview,
    String resultStorageMode,
    String resultRef,
    String resultSha256,
    Integer argumentCharCount,
    Integer argumentTokenEstimate,
    Integer resultCharCount,
    Integer resultTokenEstimate,
    Integer resultLineCount,
    Long durationMillis,
    String errorCode,
    String errorMessage,
    String redactionPolicyVersion,
    Instant startedAt,
    Instant endedAt
) {
}
