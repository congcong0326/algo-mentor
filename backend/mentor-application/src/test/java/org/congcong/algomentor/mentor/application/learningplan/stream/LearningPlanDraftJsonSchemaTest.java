package org.congcong.algomentor.mentor.application.learningplan.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningPlanDraftJsonSchemaTest {

  @Test
  void metadataSchemaIsStrictForProviderNativeStructuredOutput() {
    JsonNode metadata = LearningPlanDraftJsonSchema.schema()
        .path("properties")
        .path("metadata");

    assertThat(metadata.path("additionalProperties").asBoolean()).isFalse();
    List<String> required = new ArrayList<>();
    metadata.path("required").forEach(node -> required.add(node.asText()));
    assertThat(required).containsExactly("problemRecommendationIncomplete");
    assertThat(metadata.path("properties").has("problemRecommendationIncomplete")).isTrue();
  }
}
