package org.congcong.algomentor.llm.core.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 声明可调用的工具名称、描述、输入模式（schema）及严格性设置。
 */
public record LlmToolSpec(String name, String description, JsonNode inputSchema, boolean strict) {

  public LlmToolSpec {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("LLM tool name must not be blank");
    }
    if (description == null || description.isBlank()) {
      throw new IllegalArgumentException("LLM tool description must not be blank");
    }
    if (inputSchema == null) {
      throw new IllegalArgumentException("LLM tool input schema must not be null");
    }
  }
}
