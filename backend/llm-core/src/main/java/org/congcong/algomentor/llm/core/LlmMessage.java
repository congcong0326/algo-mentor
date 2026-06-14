package org.congcong.algomentor.llm.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record LlmMessage(
    Role role,
    List<LlmContentPart> content,
    String name,
    String toolCallId,
    Map<String, Object> metadata
) {

  public LlmMessage {
    if (role == null) {
      throw new IllegalArgumentException("LLM message role must not be null");
    }
    if (content == null || content.isEmpty()) {
      throw new IllegalArgumentException("LLM message content must not be empty");
    }
    content = List.copyOf(content);
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }

  public LlmMessage(Role role, String text) {
    this(role, List.of(new LlmContentPart.Text(text)), null, null, Map.of());
  }

  public static LlmMessage system(String text) {
    return new LlmMessage(Role.SYSTEM, text);
  }

  public static LlmMessage user(String text) {
    return new LlmMessage(Role.USER, text);
  }

  public static LlmMessage assistant(String text) {
    return new LlmMessage(Role.ASSISTANT, text);
  }

  public static LlmMessage toolResult(String toolCallId, JsonNode result) {
    if (toolCallId == null || toolCallId.isBlank()) {
      throw new IllegalArgumentException("LLM tool call id must not be blank");
    }
    return new LlmMessage(Role.TOOL, List.of(new LlmContentPart.ToolResult(result)), null, toolCallId, Map.of());
  }

  public String text() {
    return content.stream()
        .filter(LlmContentPart.Text.class::isInstance)
        .map(LlmContentPart.Text.class::cast)
        .map(LlmContentPart.Text::text)
        .collect(Collectors.joining());
  }

  public enum Role {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
  }
}
