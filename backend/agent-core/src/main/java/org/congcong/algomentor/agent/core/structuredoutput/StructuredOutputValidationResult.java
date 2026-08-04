package org.congcong.algomentor.agent.core.structuredoutput;

import com.fasterxml.jackson.databind.JsonNode;

/** 结构化输出解析与 Schema 校验结果。 */
public record StructuredOutputValidationResult(
    JsonNode structuredOutput,
    StructuredOutputValidationError error
) {

  public StructuredOutputValidationResult {
    if ((structuredOutput == null) == (error == null)) {
      throw new IllegalArgumentException(
          "Structured output validation result must contain exactly one of output or error");
    }
  }

  public static StructuredOutputValidationResult valid(JsonNode structuredOutput) {
    return new StructuredOutputValidationResult(structuredOutput, null);
  }

  public static StructuredOutputValidationResult invalid(StructuredOutputValidationError error) {
    return new StructuredOutputValidationResult(null, error);
  }

  public boolean valid() {
    return error == null;
  }
}
