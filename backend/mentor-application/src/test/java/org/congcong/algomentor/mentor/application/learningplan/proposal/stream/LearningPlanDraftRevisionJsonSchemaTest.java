package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.agent.core.structuredoutput.AgentStructuredOutputValidator;
import org.congcong.algomentor.agent.core.structuredoutput.StructuredOutputValidationResult;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.junit.jupiter.api.Test;

class LearningPlanDraftRevisionJsonSchemaTest {

  @Test
  void acceptsAValidRevisionPayloadInTheAgentCoreValidator() {
    StructuredOutputValidationResult result = new AgentStructuredOutputValidator(new ObjectMapper()).validate(
        new LlmResponseFormat.JsonSchema(
            "learning_plan_draft_revision",
            LearningPlanDraftRevisionJsonSchema.schema(),
            true),
        """
            {
              "status": "COMPILED",
              "artifactRef": "draft-revision:101:compiled"
            }
            """);

    assertThat(result.valid()).isTrue();
  }

  @Test
  void schemaOnlyContainsBoundedArtifactReferenceFields() {
    var properties = LearningPlanDraftRevisionJsonSchema.schema().path("properties");
    List<String> fields = new ArrayList<>();
    properties.fieldNames().forEachRemaining(fields::add);

    assertThat(fields).containsExactlyInAnyOrder("status", "artifactRef");
    assertThat(LearningPlanDraftRevisionJsonSchema.schema().path("additionalProperties").booleanValue()).isFalse();
  }
}
