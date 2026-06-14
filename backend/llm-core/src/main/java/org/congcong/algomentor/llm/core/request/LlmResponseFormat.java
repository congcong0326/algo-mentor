package org.congcong.algomentor.llm.core.request;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 针对文本、JSON 对象或 JSON Schema 输出所请求的响应格式。
 */
public sealed interface LlmResponseFormat
    permits LlmResponseFormat.Text, LlmResponseFormat.JsonObject, LlmResponseFormat.JsonSchema {

  /**
   * Free-form text response format.
   */
  record Text() implements LlmResponseFormat {}

  /**
   * JSON object response format without a strict schema.
   */
  record JsonObject() implements LlmResponseFormat {}

  /**
   * JSON schema response format with a named schema and strictness flag.
   */
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
