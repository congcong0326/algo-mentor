package org.congcong.algomentor.llm.core.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Arrays;

/** LLM reasoning 强度的统一协议值，wire value 同时用于 JSON、数据库和上游请求。 */
public enum LlmReasoningEffort {
  NONE("none"),
  MINIMAL("minimal"),
  LOW("low"),
  MEDIUM("medium"),
  HIGH("high"),
  XHIGH("xhigh"),
  MAX("max");

  private final String wireValue;

  LlmReasoningEffort(String wireValue) {
    this.wireValue = wireValue;
  }

  @JsonValue
  public String wireValue() {
    return wireValue;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static LlmReasoningEffort fromJson(JsonNode value) {
    if (value == null || !value.isTextual()) {
      throw new IllegalArgumentException("LLM reasoning effort must be a string");
    }
    return fromWire(value.textValue());
  }

  public static LlmReasoningEffort fromWire(String wireValue) {
    if (wireValue == null || wireValue.isBlank()) {
      throw new IllegalArgumentException("LLM reasoning effort must not be blank");
    }
    return Arrays.stream(values())
        .filter(value -> value.wireValue.equals(wireValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported LLM reasoning effort: " + wireValue));
  }
}
