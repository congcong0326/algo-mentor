package org.congcong.algomentor.mentor.application.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LearnerMemoryClaimDimensionCatalogTest {

  @Test
  void exposesTheDesignedDeclaredAndGeneralClaimDimensions() {
    assertThat(LearnerMemoryClaimDimensionCatalog.declaredDimensions()).containsExactlyInAnyOrder(
        LearnerMemoryClaimDimension.LEARNER_BACKGROUND,
        LearnerMemoryClaimDimension.GOALS_AND_INTENTS,
        LearnerMemoryClaimDimension.TIME_AND_RESOURCE_CONSTRAINTS,
        LearnerMemoryClaimDimension.LEARNING_AND_INTERACTION_PREFERENCES,
        LearnerMemoryClaimDimension.SELF_ABILITY_ASSESSMENT);
    assertThat(LearnerMemoryClaimDimensionCatalog.generalDimensions()).containsExactlyInAnyOrder(
        LearnerMemoryClaimDimension.PROBLEM_SOLVING_APPROACH,
        LearnerMemoryClaimDimension.IMPLEMENTATION_AND_ERROR_PATTERN,
        LearnerMemoryClaimDimension.LEARNING_INTERACTION_AND_INDEPENDENCE,
        LearnerMemoryClaimDimension.REVIEW_AND_GROWTH_PERFORMANCE);
  }
}
