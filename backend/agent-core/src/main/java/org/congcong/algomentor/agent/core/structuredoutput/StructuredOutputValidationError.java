package org.congcong.algomentor.agent.core.structuredoutput;

import java.util.List;

/** 模型结构化输出在客户端校验阶段产生的可回灌错误。 */
public record StructuredOutputValidationError(
    Type type,
    String message,
    List<String> details
) {

  public StructuredOutputValidationError {
    if (type == null) {
      throw new IllegalArgumentException("Structured output validation error type must not be null");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("Structured output validation error message must not be blank");
    }
    details = details == null ? List.of() : List.copyOf(details);
  }

  public String feedback() {
    if (details.isEmpty()) {
      return type.name() + ": " + message;
    }
    return type.name() + ": " + message + "\n- " + String.join("\n- ", details);
  }

  public enum Type {
    JSON_PARSE,
    JSON_TYPE,
    JSON_SCHEMA,
    UNEXPECTED_TOOL_CALL
  }
}
