package org.congcong.algomentor.api.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.congcong.algomentor.api.activity.mapper.ActivityContributionMapper;
import org.congcong.algomentor.api.activity.mapper.model.ActivityContributionRow;
import org.congcong.algomentor.api.activity.model.ActivityContributionResponse;
import org.junit.jupiter.api.Test;

class ActivityContributionServiceTest {

  private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void buildsCompleteCalendarAndComputesStreaksInRequestedTimezone() {
    ActivityContributionMapper mapper = mock(ActivityContributionMapper.class);
    when(mapper.findDailyCounts(eq(42L), eq("Asia/Shanghai"), any(), any())).thenReturn(List.of(
        new ActivityContributionRow(LocalDate.of(2026, 8, 17), 1),
        new ActivityContributionRow(LocalDate.of(2026, 8, 18), 2),
        new ActivityContributionRow(LocalDate.of(2026, 8, 19), 5)));

    ActivityContributionResponse response = new ActivityContributionService(mapper, CLOCK)
        .getContributions(42L, "Asia/Shanghai");

    assertThat(response.timezone()).isEqualTo("Asia/Shanghai");
    assertThat(response.from()).isEqualTo(LocalDate.of(2025, 8, 20));
    assertThat(response.to()).isEqualTo(LocalDate.of(2026, 8, 19));
    assertThat(response.days()).hasSize(371);
    assertThat(response.days().get(0).date()).isEqualTo(LocalDate.of(2025, 8, 17));
    assertThat(response.days().get(370).date()).isEqualTo(LocalDate.of(2026, 8, 22));
    assertThat(response.totalCount()).isEqualTo(8);
    assertThat(response.activeDays()).isEqualTo(3);
    assertThat(response.currentStreak()).isEqualTo(3);
    assertThat(response.longestStreak()).isEqualTo(3);
    assertThat(response.days().stream()
        .filter(day -> day.date().equals(LocalDate.of(2026, 8, 19)))
        .findFirst()
        .orElseThrow()
        .level()).isEqualTo(3);

    verify(mapper).findDailyCounts(
        eq(42L),
        eq("Asia/Shanghai"),
        eq(Instant.parse("2025-08-19T16:00:00Z")),
        eq(Instant.parse("2026-08-19T16:00:00Z")));
  }

  @Test
  void defaultsBlankTimezoneToUtc() {
    ActivityContributionMapper mapper = mock(ActivityContributionMapper.class);
    when(mapper.findDailyCounts(eq(42L), eq("UTC"), any(), any())).thenReturn(List.of());

    ActivityContributionResponse response = new ActivityContributionService(mapper, CLOCK)
        .getContributions(42L, " ");

    assertThat(response.timezone()).isEqualTo("UTC");
    assertThat(response.days()).hasSize(371);
  }

  @Test
  void assignsAllSubmissionCountThresholdsToTheAgreedLevels() {
    ActivityContributionMapper mapper = mock(ActivityContributionMapper.class);
    when(mapper.findDailyCounts(eq(42L), eq("Asia/Shanghai"), any(), any())).thenReturn(List.of(
        new ActivityContributionRow(LocalDate.of(2026, 8, 13), 1),
        new ActivityContributionRow(LocalDate.of(2026, 8, 14), 2),
        new ActivityContributionRow(LocalDate.of(2026, 8, 15), 3),
        new ActivityContributionRow(LocalDate.of(2026, 8, 16), 4),
        new ActivityContributionRow(LocalDate.of(2026, 8, 17), 5),
        new ActivityContributionRow(LocalDate.of(2026, 8, 18), 6),
        new ActivityContributionRow(LocalDate.of(2026, 8, 19), 7)));

    ActivityContributionResponse response = new ActivityContributionService(mapper, CLOCK)
        .getContributions(42L, "Asia/Shanghai");

    assertThat(response.days())
        .filteredOn(day -> !day.date().isBefore(LocalDate.of(2026, 8, 13))
            && !day.date().isAfter(LocalDate.of(2026, 8, 19)))
        .extracting(day -> day.level())
        .containsExactly(1, 1, 2, 2, 3, 3, 4);
  }

  @Test
  void rejectsInvalidTimezone() {
    ActivityContributionMapper mapper = mock(ActivityContributionMapper.class);

    assertThatThrownBy(() -> new ActivityContributionService(mapper, CLOCK)
        .getContributions(42L, "not-a-timezone"))
        .isInstanceOf(ActivityContributionService.ActivityTimezoneInvalidException.class);
  }
}
