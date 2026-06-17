package org.congcong.algomentor.agent.core.compaction;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.llm.core.request.LlmMessage;

public record RunMessageCompactionResult(
    List<LlmMessage> messages,
    Map<String, Object> metadata
) {

  public RunMessageCompactionResult {
    if (messages == null || messages.isEmpty()) {
      throw new IllegalArgumentException("messages must not be empty");
    }
    messages = List.copyOf(messages);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
