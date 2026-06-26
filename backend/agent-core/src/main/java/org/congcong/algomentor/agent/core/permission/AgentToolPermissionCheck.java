package org.congcong.algomentor.agent.core.permission;

import java.util.Map;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

public record AgentToolPermissionCheck(
    AgentLoopContext context,
    int stepIndex,
    LlmToolCall toolCall,
    AgentTool tool,
    Map<String, Object> trustedMetadata
) {

  public AgentToolPermissionCheck {
    if (context == null) {
      throw new IllegalArgumentException("Agent tool permission context must not be null");
    }
    if (stepIndex < 1) {
      throw new IllegalArgumentException("Agent tool permission step index must be positive");
    }
    if (toolCall == null) {
      throw new IllegalArgumentException("Agent tool permission tool call must not be null");
    }
    if (tool == null) {
      throw new IllegalArgumentException("Agent tool permission tool must not be null");
    }
    trustedMetadata = trustedMetadata == null ? Map.of() : Map.copyOf(trustedMetadata);
  }
}
