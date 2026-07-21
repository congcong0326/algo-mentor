package org.congcong.algomentor.mentor.application.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LearnerProfileContractTest {

  @Test
  void acceptsOnlyTheDesignedKindDimensionAndTagMatrix() {
    assertThat(LearnerProfileContract.isValidScope(
        LearnerProfileEntryKind.DECLARED_FACT,
        LearnerProfileDimension.GOALS_AND_INTENTS,
        null)).isTrue();
    assertThat(LearnerProfileContract.isValidScope(
        LearnerProfileEntryKind.GENERAL_OBSERVATION,
        LearnerProfileDimension.IMPLEMENTATION_AND_ERROR_PATTERN,
        null)).isTrue();
    assertThat(LearnerProfileContract.isValidScope(
        LearnerProfileEntryKind.TAG_ASSESSMENT,
        LearnerProfileDimension.TAG_MASTERY,
        9L)).isTrue();
    assertThat(LearnerProfileContract.isValidScope(
        LearnerProfileEntryKind.TAG_ASSESSMENT,
        LearnerProfileDimension.TAG_MASTERY,
        null)).isFalse();
    assertThat(LearnerProfileContract.isValidScope(
        LearnerProfileEntryKind.DECLARED_FACT,
        LearnerProfileDimension.TAG_MASTERY,
        null)).isFalse();
  }
}
