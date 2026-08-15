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
            PracticeCodeReviewConstants.JSON_JUDGE_ASSESSMENT,
            PracticeCodeReviewConstants.JSON_SCORE_EXPLANATIONS,
            "scores",
            "passed",
            "deductionReasons",
            "improvementSuggestions",
            "reviewMarkdown",
            PracticeCodeReviewConstants.JSON_AFFECTED_TAG_IDS,
            PracticeCodeReviewConstants.JSON_REVIEW_HISTORY_SUMMARY));
    assertThat(schema.path("properties")
        .path(PracticeCodeReviewConstants.JSON_REVIEW_HISTORY_SUMMARY)
        .path("type").asText()).isEqualTo("string");
    assertThat(schema.path("properties")
        .path(PracticeCodeReviewConstants.JSON_REVIEW_HISTORY_SUMMARY)
        .path("maxLength").asInt())
        .isEqualTo(PracticeCodeReviewConstants.REVIEW_HISTORY_SUMMARY_MAX_LENGTH);
  }

  @Test
  void definesRequiredJudgeAssessmentAndAllowedVerdicts() {
    JsonNode assessment = PracticeCodeReviewJsonSchema.schema()
        .path("properties")
        .path(PracticeCodeReviewConstants.JSON_JUDGE_ASSESSMENT);

    assertThat(assessment.path("additionalProperties").asBoolean()).isFalse();
    assertThat(assessment.path("required"))
        .extracting(JsonNode::asText)
        .containsExactly(
            PracticeCodeReviewConstants.JSON_JUDGE_VERDICT,
            PracticeCodeReviewConstants.JSON_VERDICT_BASIS,
            PracticeCodeReviewConstants.JSON_BLOCKING_ISSUE,
            PracticeCodeReviewConstants.JSON_MEETS_EXPECTED_COMPLEXITY,
            PracticeCodeReviewConstants.JSON_TIME_COMPLEXITY,
            PracticeCodeReviewConstants.JSON_SPACE_COMPLEXITY,
            PracticeCodeReviewConstants.JSON_EXPECTED_TIME_COMPLEXITY,
            PracticeCodeReviewConstants.JSON_CONSTRAINT_ANALYSIS);
    assertThat(assessment.path("properties")
        .path(PracticeCodeReviewConstants.JSON_JUDGE_VERDICT)
        .path("enum"))
        .extracting(JsonNode::asText)
        .containsExactly(
            "ACCEPTED",
            "LIKELY_ACCEPTED",
            "WRONG_ANSWER",
            "TIME_LIMIT_EXCEEDED",
            "MEMORY_LIMIT_EXCEEDED",
            "COMPILE_ERROR",
            "RUNTIME_ERROR",
            "UNKNOWN");
  }

  @Test
  void definesScoreDimensionRangesAndFixedLevels() {
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
    assertScoreLevels(scores, "correctness", "0", "0.5", "1", "1.5", "2", "2.5", "3", "3.5", "4");
    assertScoreLevels(scores, "complexity", "0", "0.5", "1", "1.5", "2");
    assertScoreLevels(scores, "edgeCases", "0", "0.5", "1", "1.5", "2");
    assertScoreLevels(scores, "codeQuality", "0", "0.5", "0.75", "1");
    assertScoreLevels(scores, "problemFit", "0", "0.5", "1");
    assertThat(scores.path("properties").path("total").has("enum")).isFalse();
  }

  @Test
  void requiresOneUserVisibleExplanationForEveryScoreDimension() {
    JsonNode explanations = PracticeCodeReviewJsonSchema.schema()
        .path("properties")
        .path(PracticeCodeReviewConstants.JSON_SCORE_EXPLANATIONS);

    assertThat(explanations.path("additionalProperties").asBoolean()).isFalse();
    assertThat(explanations.path("required"))
        .extracting(JsonNode::asText)
        .containsExactly(
            PracticeCodeReviewConstants.JSON_SCORE_CORRECTNESS,
            PracticeCodeReviewConstants.JSON_SCORE_COMPLEXITY,
            PracticeCodeReviewConstants.JSON_SCORE_EDGE_CASES,
            PracticeCodeReviewConstants.JSON_SCORE_CODE_QUALITY,
            PracticeCodeReviewConstants.JSON_SCORE_PROBLEM_FIT);
  }

  private void assertScoreRange(JsonNode scores, String field, int minimum, int maximum) {
    JsonNode schema = scores.path("properties").path(field);
    assertThat(schema.path("type").asText()).isEqualTo("number");
    assertThat(schema.path("minimum").asInt()).isEqualTo(minimum);
    assertThat(schema.path("maximum").asInt()).isEqualTo(maximum);
  }

  private void assertScoreLevels(JsonNode scores, String field, String... levels) {
    assertThat(scores.path("properties").path(field).path("enum"))
        .extracting(JsonNode::asText)
        .containsExactly(levels);
  }
}
