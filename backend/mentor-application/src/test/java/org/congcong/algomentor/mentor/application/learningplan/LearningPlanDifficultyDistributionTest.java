package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LearningPlanDifficultyDistributionTest {

  @Test
  void acceptsZeroAndOneHundredPercentBoundariesWhenTotalIsOneHundred() {
    assertThat(new LearningPlanDifficultyDistribution(0, 0, 100))
        .isEqualTo(new LearningPlanDifficultyDistribution(0, 0, 100));
    assertThat(new LearningPlanDifficultyDistribution(100, 0, 0))
        .isEqualTo(new LearningPlanDifficultyDistribution(100, 0, 0));
  }

  @Test
  void rejectsOutOfRangeAndNonHundredTotals() {
    assertInvalid(-1, 51, 50);
    assertInvalid(101, 0, 0);
    assertInvalid(25, 55, 10);
  }

  private void assertInvalid(int easyPercent, int mediumPercent, int hardPercent) {
    assertThatThrownBy(() -> new LearningPlanDifficultyDistribution(easyPercent, mediumPercent, hardPercent))
        .isInstanceOfSatisfying(LearningPlanException.class, exception ->
            assertThat(exception.code()).isEqualTo("LEARNING_PLAN_DIFFICULTY_DISTRIBUTION_INVALID"));
  }
}
