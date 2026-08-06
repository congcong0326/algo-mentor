package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningPlanExtensionJsonSchemaTest {

  @Test
  void phaseSchemaOnlyAcceptsFieldsNeededToExecuteTheExtension() {
    var phaseProperties = LearningPlanExtensionJsonSchema.schema()
        .path("properties")
        .path("newPhases")
        .path("items")
        .path("properties");
    List<String> phaseFields = new ArrayList<>();
    phaseProperties.fieldNames().forEachRemaining(phaseFields::add);

    assertThat(phaseFields)
        .containsExactlyInAnyOrder("phaseIndex", "title", "durationWeeks", "focus", "problems");
  }
}
