package org.congcong.algomentor.api.controller.admin.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** 工具结果预览或按范围读取的低敏响应。 */
public record AdminAiAuditToolResultResponse(
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
