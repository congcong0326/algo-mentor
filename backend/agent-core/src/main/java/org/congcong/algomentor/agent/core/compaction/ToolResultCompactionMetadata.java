package org.congcong.algomentor.agent.core.compaction;

import java.util.LinkedHashMap;
import java.util.Map;

public record ToolResultCompactionMetadata(
    String storageMode,
    String resultRef,
    Long blobId,
    Integer previewCharCount,
    Integer resultCharCount,
    Integer resultLineCount,
    String resultSha256,
    String contentType,
    boolean truncated,
    String policyVersion
) {

  public Map<String, Object> asMap() {
    Map<String, Object> values = new LinkedHashMap<>();
    put(values, "storageMode", storageMode);
    put(values, "resultRef", resultRef);
    put(values, "blobId", blobId);
    put(values, "previewCharCount", previewCharCount);
    put(values, "resultCharCount", resultCharCount);
    put(values, "resultLineCount", resultLineCount);
    put(values, "resultSha256", resultSha256);
    put(values, "contentType", contentType);
    values.put("truncated", truncated);
    put(values, "policyVersion", policyVersion);
    return values;
  }

  private void put(Map<String, Object> values, String key, Object value) {
    if (value != null) {
      values.put(key, value);
    }
  }
}
