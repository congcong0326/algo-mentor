package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LearningPlanProgressSummaryTest {

  @Test
  void fromCountsRoundsProgressPercentToOneDecimalPlace() {
    LearningPlanProgressSummary summary = LearningPlanProgressSummary.fromCounts(3, 2);

    assertThat(summary.totalProblemCount()).isEqualTo(3);
    assertThat(summary.completedProblemCount()).isEqualTo(2);
    assertThat(summary.progressPercent()).isEqualTo(66.7D);
  }
}
