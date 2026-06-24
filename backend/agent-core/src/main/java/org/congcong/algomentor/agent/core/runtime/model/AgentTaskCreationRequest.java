package org.congcong.algomentor.agent.core.runtime.model;

import java.util.Map;

public record AgentTaskCreationRequest(
    Long userId,
    String title,
    String systemPrompt,
    Map<String, Object> metadata
) {

  public AgentTaskCreationRequest {
    if (userId != null && userId < 1) {
      throw new IllegalArgumentException("Agent task user id must be positive");
    }
    title = title == null || title.isBlank() ? "practice-session" : title.trim();
    systemPrompt = systemPrompt == null ? "" : systemPrompt;
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
