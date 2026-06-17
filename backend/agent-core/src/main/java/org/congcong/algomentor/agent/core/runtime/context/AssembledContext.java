package org.congcong.algomentor.agent.core.runtime.context;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.llm.core.request.LlmMessage;

public record AssembledContext(
    List<LlmMessage> messages,
    Map<String, Object> metadata,
    int tokenEstimate
) {

  public AssembledContext {
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("Assembled context messages must not be empty");
    }
    messages = List.copyOf(messages);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    if (tokenEstimate < 0) {
      throw new IllegalArgumentException("Assembled context token estimate must not be negative");
    }
  }
}
