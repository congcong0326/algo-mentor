package org.congcong.algomentor.llm.core;

import com.fasterxml.jackson.databind.JsonNode;

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
