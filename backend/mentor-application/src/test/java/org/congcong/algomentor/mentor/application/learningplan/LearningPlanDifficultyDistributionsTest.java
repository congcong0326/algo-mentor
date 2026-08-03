package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LearningPlanDifficultyDistributionsTest {

  @Test
  void mapsEveryTemplateDifficultyPreferenceThroughTheSingleMapping() {
    assertThat(LearningPlanDifficultyDistributions.forTemplate(LearningPlanDifficultyPreference.EASY))
        .isEqualTo(new LearningPlanDifficultyDistribution(60, 35, 5));
    assertThat(LearningPlanDifficultyDistributions.forTemplate(LearningPlanDifficultyPreference.MEDIUM))
        .isEqualTo(new LearningPlanDifficultyDistribution(35, 55, 10));
    assertThat(LearningPlanDifficultyDistributions.forTemplate(LearningPlanDifficultyPreference.HARD))
        .isEqualTo(new LearningPlanDifficultyDistribution(10, 55, 35));
    assertThat(LearningPlanDifficultyDistributions.forTemplate(LearningPlanDifficultyPreference.MIXED))
        .isEqualTo(new LearningPlanDifficultyDistribution(25, 55, 20));
  }
}
