package org.congcong.algomentor.agent.core;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.llm.core.request.LlmMessage;

public record AgentRequest(
    String runId,
    String requestId,
    List<LlmMessage> messages,
    Map<String, Object> metadata
) {

  public AgentRequest(List<LlmMessage> messages) {
    this(null, null, messages, Map.of());
  }

  public AgentRequest {
    if (runId != null && runId.isBlank()) {
      throw new IllegalArgumentException("Agent request run id must not be blank");
    }
    if (requestId != null && requestId.isBlank()) {
      throw new IllegalArgumentException("Agent request id must not be blank");
    }
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("Agent request messages must not be empty");
    }
    messages = List.copyOf(messages);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public String displayTitle() {
    Object title = metadata.get("title");
    if (title instanceof String value && !value.isBlank()) {
      return value;
    }
    return "agent-request";
  }
}
