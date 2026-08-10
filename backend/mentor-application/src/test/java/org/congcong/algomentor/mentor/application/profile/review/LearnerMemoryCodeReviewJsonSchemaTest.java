package org.congcong.algomentor.mentor.application.profile.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearnerMemoryCodeReviewJsonSchemaTest {

  @Test
  void definesOnlyTheStrictOperationRootAndFourActionVariants() {
    JsonNode schema = LearnerMemoryCodeReviewJsonSchema.schema();

    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.path("required")).extracting(JsonNode::asText)
        .containsExactlyElementsOf(List.of(LearnerMemoryCodeReviewJsonSchema.OPERATIONS));
    JsonNode variants = schema.path("properties").path(LearnerMemoryCodeReviewJsonSchema.OPERATIONS)
        .path("items").path("anyOf");
    assertThat(variants).hasSize(4);
    assertThat(variants.get(0).path("properties").path(LearnerMemoryCodeReviewJsonSchema.SCOPE)
        .path("anyOf")).hasSize(2);
    assertThat(schema.findValues("oneOf")).isEmpty();
    assertThat(schema.path("properties").path(LearnerMemoryCodeReviewJsonSchema.OPERATIONS).path("maxItems").asInt())
        .isEqualTo(12);
  }
}
