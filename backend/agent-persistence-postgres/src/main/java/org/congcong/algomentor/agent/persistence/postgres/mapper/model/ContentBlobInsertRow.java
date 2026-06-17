package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record ContentBlobInsertRow(
    Long id,
    String scopeType,
    Long scopeId,
    String contentType,
    String storageMode,
    String contentText,
    byte[] contentBytes,
    String uri,
    String sha256,
    Integer charCount,
    Long byteCount,
    Integer lineCount,
    String redactionPolicyVersion,
    JsonNode metadata,
    Instant createdAt
) {
}
