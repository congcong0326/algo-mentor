package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCandidate;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemSearch;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStructuredOutputMapper;
import org.junit.jupiter.api.Test;

class LearningPlanDraftRevisionStructuredOutputMapperTest {

  @Test
  void mapsUpdatedBriefFieldsAndRestoresServerControlledRunSettings() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    LearningPlanDraftRevisionStructuredOutputMapper mapper = new LearningPlanDraftRevisionStructuredOutputMapper(
        objectMapper,
        new LearningPlanDraftStructuredOutputMapper(objectMapper, emptyCatalog()),
        new LearningPlanDraftValidator());
    LearningPlanBrief currentBrief = new LearningPlanBrief(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "当前目标",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        "当前限制",
        true,
        LearningPlanContentLocale.EN_US);

    LearningPlanDraftRevisionOutput output = mapper.map(objectMapper.readTree("""
        {
          "resolvedBrief": {
            "intent": "TOPIC_BREAKTHROUGH",
            "objective": "掌握图论专题",
            "durationWeeks": 1,
            "level": "ADVANCED",
            "weeklyHours": 10,
            "programmingLanguage": "Kotlin",
            "difficultyDistribution": {"easyPercent": 20, "mediumPercent": 50, "hardPercent": 30},
            "topicPreferences": ["Graph", "Shortest Path"],
            "additionalConstraints": "每周安排复盘",
            "personalizationEnabled": false,
            "contentLocale": "zh-CN"
          },
          "generatedContent": {
            "title": "图论专题",
            "summary": "summary",
            "phases": [{
              "phaseIndex": 1,
              "title": "图基础",
              "durationWeeks": 1,
              "focus": "Graph",
              "problems": []
            }]
          }
        }
        """), currentBrief);

    assertThat(output.resolvedBrief()).isEqualTo(new LearningPlanBrief(
        LearningPlanIntent.TOPIC_BREAKTHROUGH,
        "掌握图论专题",
        1,
        LearningPlanLevel.ADVANCED,
        10,
        "Kotlin",
        new LearningPlanDifficultyDistribution(20, 50, 30),
        List.of("Graph", "Shortest Path"),
        "每周安排复盘",
        true,
        LearningPlanContentLocale.EN_US));
    assertThat(output.generatedPlan())
        .extracting(plan -> plan.intent(), plan -> plan.objective(), plan -> plan.durationWeeks(),
            plan -> plan.weeklyHours(), plan -> plan.difficultyDistribution(), plan -> plan.topicPreferences(),
            plan -> plan.additionalConstraints())
        .containsExactly(
            LearningPlanIntent.TOPIC_BREAKTHROUGH,
            "掌握图论专题",
            1,
            10,
            new LearningPlanDifficultyDistribution(20, 50, 30),
            List.of("Graph", "Shortest Path"),
            "每周安排复盘");
    assertThat(output.generatedPlan().contentLocale()).isEqualTo(LearningPlanContentLocale.EN_US);
    assertThat(output.generatedPlan().metadata()).containsEntry("personalizationEnabled", true);
  }

  private LearningPlanProblemCatalog emptyCatalog() {
    return new LearningPlanProblemCatalog() {
      @Override
      public List<LearningPlanProblemCandidate> searchProblems(LearningPlanProblemSearch search) {
        return List.of();
      }

      @Override
      public Optional<LearningPlanProblemCandidate> findBySlug(String slug) {
        return Optional.empty();
      }
    };
  }
}
