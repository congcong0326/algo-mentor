package org.congcong.algomentor.agent.core.toolresult;

public record StoredToolResult(
    String resultRef,
    String contentType,
    String contentText,
    String sha256,
    int charCount,
    int lineCount,
    Long blobId,
    Long toolCallDbId
) {

  public StoredToolResult {
    if (resultRef == null || resultRef.isBlank()) {
      throw new IllegalArgumentException("resultRef must not be blank");
    }
    if (contentType == null || contentType.isBlank()) {
      throw new IllegalArgumentException("contentType must not be blank");
    }
    contentText = contentText == null ? "" : contentText;
    if (sha256 == null || sha256.isBlank()) {
      throw new IllegalArgumentException("sha256 must not be blank");
    }
    if (charCount < 0) {
      throw new IllegalArgumentException("charCount must not be negative");
    }
    if (lineCount < 0) {
      throw new IllegalArgumentException("lineCount must not be negative");
    }
  }
}
