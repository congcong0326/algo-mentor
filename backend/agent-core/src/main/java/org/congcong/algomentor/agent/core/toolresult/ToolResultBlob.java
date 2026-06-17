package org.congcong.algomentor.agent.core.toolresult;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultJsonKeys;

public record ToolResultBlob(
    Long id,
    String resultRef,
    String scopeType,
    Long scopeId,
    String contentType,
    String storageMode,
    String contentText,
    String sha256,
    int charCount,
    long byteCount,
    int lineCount,
    String redactionPolicyVersion,
    JsonNode metadata,
    Instant createdAt
) {

  public ToolResultBlob {
    if (resultRef == null || resultRef.isBlank()) {
      throw new IllegalArgumentException("resultRef must not be blank");
    }
    if (contentType == null || contentType.isBlank()) {
      throw new IllegalArgumentException("contentType must not be blank");
    }
    if (contentText == null) {
      contentText = "";
    }
    if (sha256 == null || sha256.isBlank()) {
      throw new IllegalArgumentException("sha256 must not be blank");
    }
    metadata = metadata == null ? null : metadata.deepCopy();
    createdAt = createdAt == null ? Instant.now() : createdAt;
  }

  public Map<String, Object> sourceMetadata() {
    return Map.of(
        AgentToolResultJsonKeys.RESULT_REF, resultRef,
        AgentToolResultJsonKeys.CONTENT_TYPE, contentType,
        AgentToolResultJsonKeys.CHAR_COUNT, charCount,
        AgentToolResultJsonKeys.LINE_COUNT, lineCount,
        "sha256", sha256,
        "redactionPolicyVersion", redactionPolicyVersion == null ? "" : redactionPolicyVersion);
  }
}
