package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolCallStorageUpdate(
    long runId,
    int stepIndex,
    String toolCallId,
    String resultStorageMode,
    Long resultBlobId,
    JsonNode resultPreviewJson,
    String resultRef,
    Integer resultLineCount,
    String resultSha256
) {
}
