package org.congcong.algomentor.llm.core.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 由模型请求的工具调用，包含 ID、名称和 JSON 参数。
 */
public record LlmToolCall(String id, String name, JsonNode arguments) {

  public LlmToolCall {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("LLM tool call id must not be blank");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("LLM tool call name must not be blank");
    }
    if (arguments == null) {
      throw new IllegalArgumentException("LLM tool call arguments must not be null");
    }
  }
}
