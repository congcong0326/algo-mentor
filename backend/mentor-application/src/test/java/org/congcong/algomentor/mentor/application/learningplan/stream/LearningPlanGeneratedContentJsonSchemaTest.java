package org.congcong.algomentor.mentor.application.learningplan.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningPlanGeneratedContentJsonSchemaTest {

  @Test
  void rootSchemaOnlyAcceptsGeneratedContent() {
    JsonNode schema = LearningPlanGeneratedContentJsonSchema.schema();
    JsonNode properties = schema.path("properties");
    List<String> rootFields = new ArrayList<>();
    properties.fieldNames().forEachRemaining(rootFields::add);

    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(rootFields)
        .containsExactlyInAnyOrder("title", "summary", "phases");
    assertThat(properties.has("intent")).isFalse();
    assertThat(properties.has("objective")).isFalse();
    assertThat(properties.has("durationWeeks")).isFalse();
    assertThat(properties.has("level")).isFalse();
    assertThat(properties.has("weeklyHours")).isFalse();
    assertThat(properties.has("programmingLanguage")).isFalse();
    assertThat(properties.has("difficultyDistribution")).isFalse();
    assertThat(properties.has("topicPreferences")).isFalse();
    assertThat(properties.has("additionalConstraints")).isFalse();
    assertThat(properties.has("metadata")).isFalse();
  }

  @Test
  void phaseSchemaOnlyAcceptsFieldsNeededToExecuteThePlan() {
    JsonNode phases = LearningPlanGeneratedContentJsonSchema.schema()
        .path("properties")
        .path("phases");
    JsonNode phaseProperties = phases
        .path("items")
        .path("properties");
    List<String> phaseFields = new ArrayList<>();
    phaseProperties.fieldNames().forEachRemaining(phaseFields::add);

    assertThat(phaseFields)
        .containsExactlyInAnyOrder("phaseIndex", "title", "durationWeeks", "focus", "problems");
    assertThat(phases.path("maxItems").asInt()).isEqualTo(6);
    assertThat(phaseProperties.path("phaseIndex").path("maximum").asInt()).isEqualTo(6);
  }
}
