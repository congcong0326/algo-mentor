package org.congcong.algomentor.api.ability.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.congcong.algomentor.api.ability.mapper.AbilityProfileMapper;
import org.congcong.algomentor.api.ability.mapper.model.AbilityTagScoreRow;
import org.congcong.algomentor.api.ability.model.AbilityProfileResponse;
import org.junit.jupiter.api.Test;

class AbilityProfileServiceTest {

  @Test
  void returnsZeroScoresWhenTagHasNoReviews() {
    AbilityProfileMapper mapper = mock(AbilityProfileMapper.class);
    when(mapper.findCommonTagScores(42L, 20)).thenReturn(List.of(
        row("array", "数组", 120L, 0L, null)));
    AbilityProfileService service = new AbilityProfileService(mapper);

    AbilityProfileResponse response = service.getProfile(42L);

    assertThat(response.tags()).hasSize(1);
    assertThat(response.tags().get(0).rawAverageScore()).isEqualByComparingTo("0.0");
    assertThat(response.tags().get(0).abilityScore()).isEqualByComparingTo("0.0");
  }

  @Test
  void weightsOnePerfectReviewedProblemToTwoPoints() {
    AbilityProfileResponse response = profileFor(row("hash-table", "哈希表", 90L, 1L, new BigDecimal("10.0")));

    assertThat(response.tags().get(0).rawAverageScore()).isEqualByComparingTo("10.0");
    assertThat(response.tags().get(0).abilityScore()).isEqualByComparingTo("2.0");
  }

  @Test
  void weightsThreeEightPointReviewedProblemsToThreePointFour() {
    AbilityProfileResponse response = profileFor(row("dynamic-programming", "动态规划", 80L, 3L, new BigDecimal("8.0")));

    assertThat(response.tags().get(0).rawAverageScore()).isEqualByComparingTo("8.0");
    assertThat(response.tags().get(0).abilityScore()).isEqualByComparingTo("3.4");
  }

  @Test
  void computesAbilityScoreBeforeRoundingRawAverageForResponse() {
    AbilityProfileResponse response = profileFor(row("stack", "栈", 60L, 3L, new BigDecimal("3.35")));

    assertThat(response.tags().get(0).rawAverageScore()).isEqualByComparingTo("3.4");
    assertThat(response.tags().get(0).abilityScore()).isEqualByComparingTo("1.4");
  }

  @Test
  void weightsEightEightPointReviewedProblemsToFivePointThree() {
    AbilityProfileResponse response = profileFor(row("tree", "树", 70L, 8L, new BigDecimal("8.0")));

    assertThat(response.tags().get(0).rawAverageScore()).isEqualByComparingTo("8.0");
    assertThat(response.tags().get(0).abilityScore()).isEqualByComparingTo("5.3");
  }

  @Test
  void includesProfileScopeContract() {
    AbilityProfileResponse response = profileFor(row("array", "数组", 120L, 0L, null));

    assertThat(response.scope().minProblemCount()).isEqualTo(20);
    assertThat(response.scope().scorePrecision()).isEqualTo(1);
    assertThat(response.scope().latestReviewOnly()).isTrue();
    assertThat(response.scope().conservativeWeight()).isEqualTo(4);
  }

  private AbilityProfileResponse profileFor(AbilityTagScoreRow row) {
    AbilityProfileMapper mapper = mock(AbilityProfileMapper.class);
    when(mapper.findCommonTagScores(42L, 20)).thenReturn(List.of(row));
    return new AbilityProfileService(mapper).getProfile(42L);
  }

  private AbilityTagScoreRow row(
      String tag,
      String label,
      long problemCount,
      long reviewedProblemCount,
      BigDecimal rawAverageScore
  ) {
    return new AbilityTagScoreRow(tag, label, problemCount, reviewedProblemCount, rawAverageScore);
  }
}
