package org.congcong.algomentor.agent.core.compaction;

import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultJsonKeys;

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
    put(values, AgentToolResultJsonKeys.RESULT_REF, resultRef);
    put(values, "blobId", blobId);
    put(values, "previewCharCount", previewCharCount);
    put(values, "resultCharCount", resultCharCount);
    put(values, "resultLineCount", resultLineCount);
    put(values, "resultSha256", resultSha256);
    put(values, AgentToolResultJsonKeys.CONTENT_TYPE, contentType);
    values.put(AgentToolResultJsonKeys.TRUNCATED, truncated);
    put(values, AgentRuntimeMetadataKeys.POLICY_VERSION, policyVersion);
    return values;
  }

  private void put(Map<String, Object> values, String key, Object value) {
    if (value != null) {
      values.put(key, value);
    }
  }
}
