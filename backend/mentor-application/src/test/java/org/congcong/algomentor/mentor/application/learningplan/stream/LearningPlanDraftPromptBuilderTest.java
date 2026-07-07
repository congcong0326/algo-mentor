package org.congcong.algomentor.mentor.application.learningplan.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.junit.jupiter.api.Test;

class LearningPlanDraftPromptBuilderTest {

  @Test
  void injectsCapacityBudgetFields() {
    LearningPlanDraftPromptBuilder builder = new LearningPlanDraftPromptBuilder(new LearningPlanLoadService());

    String userPrompt = builder.build(new LearningPlanDraftCommand(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 后端算法面试",
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        LearningPlanDifficultyPreference.MEDIUM,
        true,
        List.of("Array"))).get(1).text();

    assertThat(userPrompt)
        .contains("weeklyCapacityPoints: 6.0")
        .contains("totalCapacityPoints: 24.0")
        .contains("targetLoadRange: 18.0-26.4")
        .contains("loadPolicy: FIT_USER_BUDGET");
  }
}
