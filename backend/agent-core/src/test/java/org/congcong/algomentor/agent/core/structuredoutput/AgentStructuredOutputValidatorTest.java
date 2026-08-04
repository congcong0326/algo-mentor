package org.congcong.algomentor.agent.core.structuredoutput;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.junit.jupiter.api.Test;

class AgentStructuredOutputValidatorTest {

  private final AgentStructuredOutputValidator validator =
      new AgentStructuredOutputValidator(new ObjectMapper());

  @Test
  void acceptsJsonThatMatchesTheSchema() {
    StructuredOutputValidationResult result = validator.validate(schema(), "{\"days\":7}");

    assertThat(result.valid()).isTrue();
    assertThat(result.structuredOutput().path("days").asInt()).isEqualTo(7);
  }

  @Test
  void rejectsJsonThatViolatesTheSchema() {
    StructuredOutputValidationResult result = validator.validate(schema(), "{\"days\":\"seven\"}");

    assertThat(result.valid()).isFalse();
    assertThat(result.error().type()).isEqualTo(StructuredOutputValidationError.Type.JSON_SCHEMA);
    assertThat(result.error().details()).anyMatch(detail -> detail.contains("days"));
  }

  @Test
  void rejectsTrailingProseAfterAJsonValue() {
    StructuredOutputValidationResult result = validator.validate(
        new LlmResponseFormat.JsonObject(),
        "{\"days\":7} done");

    assertThat(result.valid()).isFalse();
    assertThat(result.error().type()).isEqualTo(StructuredOutputValidationError.Type.JSON_PARSE);
  }

  @Test
  void rejectsNaturalLanguageBeforeAJsonValue() {
    StructuredOutputValidationResult result = validator.validate(
        new LlmResponseFormat.JsonObject(),
        "According to the revision request: {\"days\":7}");

    assertThat(result.valid()).isFalse();
    assertThat(result.error().type()).isEqualTo(StructuredOutputValidationError.Type.JSON_PARSE);
  }

  @Test
  void requiresAnObjectForJsonObjectResponseFormat() {
    StructuredOutputValidationResult result = validator.validate(
        new LlmResponseFormat.JsonObject(),
        "[1,2,3]");

    assertThat(result.valid()).isFalse();
    assertThat(result.error().type()).isEqualTo(StructuredOutputValidationError.Type.JSON_TYPE);
  }

  private LlmResponseFormat.JsonSchema schema() {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("type", "object");
    root.put("additionalProperties", false);
    root.putObject("properties").putObject("days").put("type", "integer");
    root.putArray("required").add("days");
    return new LlmResponseFormat.JsonSchema("plan", root, true);
  }
}
