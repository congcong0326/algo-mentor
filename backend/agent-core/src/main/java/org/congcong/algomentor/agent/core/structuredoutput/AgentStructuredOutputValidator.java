package org.congcong.algomentor.agent.core.structuredoutput;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;

/** 对 provider-native 结果执行严格 JSON 解析和本地 JSON Schema 校验。 */
public final class AgentStructuredOutputValidator {

  private static final int MAX_VALIDATION_DETAILS = 8;
  private static final int MAX_DETAIL_CHARS = 400;

  private final ObjectMapper objectMapper;
  private final JsonSchemaFactory schemaFactory;

  public AgentStructuredOutputValidator(ObjectMapper objectMapper) {
    this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
  }

  public StructuredOutputValidationResult validate(LlmResponseFormat responseFormat, String content) {
    JsonNode output;
    try {
      output = objectMapper.readerFor(JsonNode.class)
          .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .readValue(content == null ? "" : content);
    } catch (JsonProcessingException exception) {
      return invalid(
          StructuredOutputValidationError.Type.JSON_PARSE,
          exception.getOriginalMessage(),
          List.of());
    }
    if (output == null || output.isMissingNode()) {
      return invalid(
          StructuredOutputValidationError.Type.JSON_PARSE,
          "Structured output is empty",
          List.of());
    }
    if (responseFormat instanceof LlmResponseFormat.JsonObject && !output.isObject()) {
      return invalid(
          StructuredOutputValidationError.Type.JSON_TYPE,
          "Structured output must be a JSON object",
          List.of("actualType=" + output.getNodeType().name()));
    }
    if (responseFormat instanceof LlmResponseFormat.JsonSchema jsonSchema) {
      Set<ValidationMessage> violations = schemaFactory.getSchema(jsonSchema.schema()).validate(output);
      if (!violations.isEmpty()) {
        List<String> details = violations.stream()
            .sorted(Comparator
                .comparing((ValidationMessage violation) -> violation.getInstanceLocation().toString())
                .thenComparing(ValidationMessage::getCode)
                .thenComparing(ValidationMessage::getMessage))
            .limit(MAX_VALIDATION_DETAILS)
            .map(this::formatViolation)
            .toList();
        return invalid(
            StructuredOutputValidationError.Type.JSON_SCHEMA,
            "Structured output does not match the requested JSON Schema (violations="
                + violations.size() + ")",
            details);
      }
    }
    return StructuredOutputValidationResult.valid(output);
  }

  private StructuredOutputValidationResult invalid(
      StructuredOutputValidationError.Type type,
      String message,
      List<String> details
  ) {
    String effectiveMessage = message == null || message.isBlank()
        ? "Structured output validation failed"
        : truncate(message);
    return StructuredOutputValidationResult.invalid(
        new StructuredOutputValidationError(type, effectiveMessage, details));
  }

  private String formatViolation(ValidationMessage violation) {
    String location = violation.getInstanceLocation() == null
        ? "$"
        : violation.getInstanceLocation().toString();
    return truncate(location + " [" + violation.getCode() + "]: " + violation.getError());
  }

  private String truncate(String value) {
    if (value.length() <= MAX_DETAIL_CHARS) {
      return value;
    }
    return value.substring(0, MAX_DETAIL_CHARS - 3) + "...";
  }
}
