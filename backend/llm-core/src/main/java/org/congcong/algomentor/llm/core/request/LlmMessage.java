package org.congcong.algomentor.llm.core.request;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;

/**
 * 与大语言模型（LLM）交互的聊天消息，包含角色、内容片段及工具关联。
 */
public record LlmMessage(
    Role role,
    List<LlmContentPart> content,
    String name,
    String toolCallId,
    Map<String, Object> metadata
) {

  private static final String TOOL_CALLS_METADATA_KEY = "toolCalls";

  public LlmMessage {
    if (role == null) {
      throw new IllegalArgumentException("LLM message role must not be null");
    }
    if (content == null || (content.isEmpty() && role != Role.ASSISTANT)) {
      throw new IllegalArgumentException("LLM message content must not be empty");
    }
    if (role == Role.TOOL && (toolCallId == null || toolCallId.isBlank())) {
      throw new IllegalArgumentException("LLM tool message must include tool call id");
    }
    if (role != Role.TOOL && toolCallId != null) {
      throw new IllegalArgumentException("LLM non-tool message must not include tool call id");
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

  public static LlmMessage assistant() {
    return new LlmMessage(Role.ASSISTANT, List.of(), null, null, Map.of());
  }

  public static LlmMessage assistantToolCalls(List<LlmToolCall> toolCalls) {
    if (toolCalls == null || toolCalls.isEmpty()) {
      throw new IllegalArgumentException("LLM assistant tool calls must not be empty");
    }
    return new LlmMessage(
        Role.ASSISTANT,
        List.of(),
        null,
        null,
        Map.of(TOOL_CALLS_METADATA_KEY, List.copyOf(toolCalls)));
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

  public List<LlmToolCall> toolCalls() {
    Object value = metadata.get(TOOL_CALLS_METADATA_KEY);
    if (!(value instanceof List<?> items)) {
      return List.of();
    }
    return items.stream()
        .filter(LlmToolCall.class::isInstance)
        .map(LlmToolCall.class::cast)
        .toList();
  }

  /**
   * Supported chat roles in the LLM conversation contract.
   */
  public enum Role {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
  }
}
