package org.congcong.algomentor.mentor.application.profile.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class CodeReviewProfileJsonSchemaTest {

  @Test
  void definesTheStrictRootObjectRequiredByOpenAiResponses() {
    JsonNode schema = CodeReviewProfileJsonSchema.schema();

    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.path("required")).extracting(JsonNode::asText)
        .containsExactlyElementsOf(List.of(
            CodeReviewProfileJsonSchema.GENERAL_OBSERVATIONS,
            CodeReviewProfileJsonSchema.TAG_ASSESSMENTS));
  }
}
