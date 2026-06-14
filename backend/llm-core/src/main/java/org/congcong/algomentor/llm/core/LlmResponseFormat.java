package org.congcong.algomentor.llm.core;

import com.fasterxml.jackson.databind.JsonNode;

public sealed interface LlmResponseFormat
    permits LlmResponseFormat.Text, LlmResponseFormat.JsonObject, LlmResponseFormat.JsonSchema {

  record Text() implements LlmResponseFormat {}

  record JsonObject() implements LlmResponseFormat {}

  record JsonSchema(String name, JsonNode schema, boolean strict) implements LlmResponseFormat {
    public JsonSchema {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("LLM JSON schema name must not be blank");
      }
      if (schema == null) {
        throw new IllegalArgumentException("LLM JSON schema must not be null");
      }
    }
  }
}
