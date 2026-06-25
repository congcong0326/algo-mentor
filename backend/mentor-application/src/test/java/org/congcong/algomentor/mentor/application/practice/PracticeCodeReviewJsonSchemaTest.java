package org.congcong.algomentor.mentor.application.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewJsonSchemaTest {

  @Test
  void requiresStructuredReviewTopLevelFieldsAndDisallowsExtras() {
    JsonNode schema = PracticeCodeReviewJsonSchema.schema();

    assertThat(schema.path("type").asText()).isEqualTo("object");
    assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.path("required"))
        .extracting(JsonNode::asText)
        .containsExactlyElementsOf(List.of(
            "isCodeSubmission",
            "belongsToCurrentProblem",
            "isCompleteLeetCodeSolution",
            "language",
            "rawCode",
            "normalizedCode",
            "evidence",
            "contextSummary",
            "scores",
            "passed",
            "deductionReasons",
            "improvementSuggestions",
            "reviewMarkdown"));
  }

  @Test
  void definesScoreDimensionRanges() {
    JsonNode scores = PracticeCodeReviewJsonSchema.schema().path("properties").path("scores");

    assertThat(scores.path("additionalProperties").asBoolean()).isFalse();
    assertThat(scores.path("required"))
        .extracting(JsonNode::asText)
        .containsExactly("correctness", "complexity", "edgeCases", "codeQuality", "problemFit", "total");
    assertScoreRange(scores, "correctness", 0, 4);
    assertScoreRange(scores, "complexity", 0, 2);
    assertScoreRange(scores, "edgeCases", 0, 2);
    assertScoreRange(scores, "codeQuality", 0, 1);
    assertScoreRange(scores, "problemFit", 0, 1);
    assertScoreRange(scores, "total", 0, 10);
  }

  private void assertScoreRange(JsonNode scores, String field, int minimum, int maximum) {
    JsonNode schema = scores.path("properties").path(field);
    assertThat(schema.path("type").asText()).isEqualTo("number");
    assertThat(schema.path("minimum").asInt()).isEqualTo(minimum);
    assertThat(schema.path("maximum").asInt()).isEqualTo(maximum);
  }
}
