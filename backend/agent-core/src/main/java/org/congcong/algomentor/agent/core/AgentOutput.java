package org.congcong.algomentor.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Agent run 确认完成后的最终 assistant 输出。
 */
public record AgentOutput(
    String text,
    JsonNode structured,
    String schemaName,
    String schemaVersion,
    Map<String, Object> metadata
) {

  public AgentOutput {
    text = text == null ? "" : text;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public boolean hasStructuredOutput() {
    return structured != null && !structured.isNull();
  }
}
