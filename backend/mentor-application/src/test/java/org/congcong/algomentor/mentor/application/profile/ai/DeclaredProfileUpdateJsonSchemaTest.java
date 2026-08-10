package org.congcong.algomentor.mentor.application.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

class DeclaredProfileUpdateJsonSchemaTest {

  @Test
  void definesTheStrictRootObjectRequiredByOpenAiResponses() {
    JsonNode schema = DeclaredProfileUpdateJsonSchema.schema();

    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.path("required")).extracting(JsonNode::asText)
        .containsExactly(DeclaredProfileUpdateJsonSchema.OPERATIONS);
    assertThat(schema.path("properties").path(DeclaredProfileUpdateJsonSchema.OPERATIONS).path("type").asText())
        .isEqualTo("array");
    assertThat(schema.path("properties").path(DeclaredProfileUpdateJsonSchema.OPERATIONS)
        .path("items").path("anyOf")).hasSize(3);
    assertThat(schema.findValues("oneOf")).isEmpty();
  }
}
