package org.congcong.algomentor.agent.core.prompt;

import java.util.List;
import java.util.Map;
import org.congcong.algomentor.llm.core.request.LlmMessage;

public record PromptAssembly(
    PromptProfile profile,
    List<LlmMessage> canonicalMessages,
    List<RenderedPromptSection> renderedSections,
    List<PromptSectionSnapshot> snapshots,
    Map<String, Object> metadata,
    int tokenEstimate
) {

  public PromptAssembly {
    if (profile == null) {
      throw new IllegalArgumentException("Prompt assembly profile must not be null");
    }
    if (canonicalMessages == null || canonicalMessages.isEmpty()) {
      throw new IllegalArgumentException("Prompt assembly canonical messages must not be empty");
    }
    canonicalMessages = List.copyOf(canonicalMessages);
    renderedSections = renderedSections == null ? List.of() : List.copyOf(renderedSections);
    snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    if (tokenEstimate < 0) {
      throw new IllegalArgumentException("Prompt assembly token estimate must not be negative");
    }
  }
}
