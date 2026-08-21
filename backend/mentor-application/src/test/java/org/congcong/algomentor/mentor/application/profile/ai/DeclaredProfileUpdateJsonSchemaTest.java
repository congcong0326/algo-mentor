package org.congcong.algomentor.mentor.application.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimDimension;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent;
import org.junit.jupiter.api.Test;

class DeclaredProfileUpdateJsonSchemaTest {

  @Test
  void definesTheStrictRootObjectAndOnlyTrustedDeclareAndCorrectionActions() {
    JsonNode schema = DeclaredProfileUpdateJsonSchema.schema(List.of(
        candidate(LearnerMemoryClaimDimension.GOALS_AND_INTENTS, DeclaredProfileUpdateIntent.DECLARE, List.of()),
        candidate(
            LearnerMemoryClaimDimension.LEARNING_AND_INTERACTION_PREFERENCES,
            DeclaredProfileUpdateIntent.CORRECT,
            List.of(new DeclaredProfileUpdateAgentInput.ActiveClaim(42L, "Ask before hints")))));

    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.path("required")).extracting(JsonNode::asText)
        .containsExactly(DeclaredProfileUpdateJsonSchema.OPERATIONS);
    assertThat(schema.path("properties").path(DeclaredProfileUpdateJsonSchema.OPERATIONS).path("type").asText())
        .isEqualTo("array");
    JsonNode variants = schema.path("properties").path(DeclaredProfileUpdateJsonSchema.OPERATIONS)
        .path("items").path("anyOf");
    assertThat(variants).hasSize(3);
    assertThat(variants.get(0).path("properties").path(DeclaredProfileUpdateJsonSchema.OPERATION_DIMENSION)
        .path("enum")).extracting(JsonNode::asText)
            .containsExactly(LearnerMemoryClaimDimension.GOALS_AND_INTENTS.name());
    assertThat(variants.get(1).path("properties").path(DeclaredProfileUpdateJsonSchema.OPERATION_TARGET_REVISION_ID)
        .path("enum")).extracting(JsonNode::asLong).containsExactly(42L);
    assertThat(schema.findValues("oneOf")).isEmpty();
  }

  @Test
  void allowsOnlyNoChangeWhenACorrectionHasNoTrustedTarget() {
    JsonNode schema = DeclaredProfileUpdateJsonSchema.schema(List.of(
        candidate(
            LearnerMemoryClaimDimension.LEARNING_AND_INTERACTION_PREFERENCES,
            DeclaredProfileUpdateIntent.CORRECT,
            List.of())));

    JsonNode operations = schema.path("properties").path(DeclaredProfileUpdateJsonSchema.OPERATIONS);
    assertThat(operations.path("maxItems").asInt()).isZero();
    assertThat(operations.has("items")).isFalse();
  }

  private DeclaredProfileUpdateAgentInput.Candidate candidate(
      LearnerMemoryClaimDimension dimension,
      DeclaredProfileUpdateIntent intent,
      List<DeclaredProfileUpdateAgentInput.ActiveClaim> activeClaims) {
    return new DeclaredProfileUpdateAgentInput.Candidate(
        dimension, "A stable learner fact", intent, activeClaims);
  }
}
