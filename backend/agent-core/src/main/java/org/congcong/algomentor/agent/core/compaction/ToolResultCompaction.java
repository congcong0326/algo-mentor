package org.congcong.algomentor.agent.core.compaction;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record ToolResultCompaction(
    JsonNode visibleResult,
    ToolResultCompactionMetadata metadata
) {

  public ToolResultCompaction {
    if (visibleResult == null) {
      throw new IllegalArgumentException("visibleResult must not be null");
    }
    if (metadata == null) {
      throw new IllegalArgumentException("metadata must not be null");
    }
  }

  public Map<String, Object> requestMetadata() {
    return metadata.asMap();
  }
}
