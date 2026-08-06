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
              "resolvedBrief": {
                "intent": "TOPIC_BREAKTHROUGH",
                "objective": "Master graph algorithms",
                "durationWeeks": 1,
                "level": "INTERMEDIATE",
                "weeklyHours": 6,
                "programmingLanguage": "Java",
                "difficultyDistribution": {
                  "easyPercent": 30,
                  "mediumPercent": 60,
                  "hardPercent": 10
                },
                "topicPreferences": ["Graph"],
                "additionalConstraints": null,
                "personalizationEnabled": true,
                "contentLocale": "zh-CN"
              },
              "generatedContent": {
                "title": "Graph Plan",
                "summary": "Build a reliable graph foundation.",
                "phases": [{
                  "phaseIndex": 1,
                  "title": "Graph Basics",
                  "durationWeeks": 1,
                  "focus": "Traversal",
                  "problems": []
                }]
              }
            }
            """);

    assertThat(result.valid()).isTrue();
  }

  @Test
  void resolvedBriefSchemaOnlyContainsCurrentBriefFields() {
    var properties = LearningPlanDraftRevisionJsonSchema.schema()
        .path("properties")
        .path("resolvedBrief")
        .path("properties");
    List<String> fields = new ArrayList<>();
    properties.fieldNames().forEachRemaining(fields::add);

    assertThat(fields).containsExactlyInAnyOrder(
        "intent",
        "objective",
        "durationWeeks",
        "level",
        "weeklyHours",
        "programmingLanguage",
        "difficultyDistribution",
        "topicPreferences",
        "additionalConstraints",
        "personalizationEnabled",
        "contentLocale");
  }
}
