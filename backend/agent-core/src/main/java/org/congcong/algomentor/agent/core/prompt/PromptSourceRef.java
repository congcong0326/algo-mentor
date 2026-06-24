package org.congcong.algomentor.agent.core.prompt;

import java.util.Map;

public record PromptSourceRef(
    String type,
    String id,
    Map<String, Object> attributes
) {

  public static PromptSourceRef none() {
    return new PromptSourceRef("none", null, Map.of());
  }

  public PromptSourceRef {
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("Prompt source ref type must not be blank");
    }
    if (id != null && id.isBlank()) {
      throw new IllegalArgumentException("Prompt source ref id must not be blank");
    }
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }
}
