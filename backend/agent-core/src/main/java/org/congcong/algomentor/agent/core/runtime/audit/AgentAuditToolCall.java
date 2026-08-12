package org.congcong.algomentor.agent.core.runtime.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 脱敏工具调用与模型可见结果摘要。 */
public record AgentAuditToolCall(
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
    String errorCode,
    String errorMessage,
    String redactionPolicyVersion,
    Instant startedAt,
    Instant endedAt
) {
}
