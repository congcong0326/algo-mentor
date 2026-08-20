package org.congcong.algomentor.api.activity.service;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.congcong.algomentor.api.activity.mapper.ActivityContributionMapper;
import org.congcong.algomentor.api.activity.mapper.model.ActivityContributionRow;
import org.congcong.algomentor.api.activity.model.ActivityContributionDayResponse;
import org.congcong.algomentor.api.activity.model.ActivityContributionResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ActivityContributionService {

  public static final int LOOKBACK_DAYS = 365;
  public static final String DEFAULT_TIMEZONE = "UTC";

  private final Supplier<ActivityContributionMapper> mapperSupplier;
  private final Clock clock;

  @Autowired
  public ActivityContributionService(
      ObjectProvider<ActivityContributionMapper> mapperProvider,
      ObjectProvider<Clock> clockProvider
  ) {
    this(mapperProvider::getIfAvailable, clockProvider.getIfAvailable(Clock::systemUTC));
  }

  ActivityContributionService(ActivityContributionMapper mapper, Clock clock) {
    this(() -> mapper, clock);
  }

  private ActivityContributionService(Supplier<ActivityContributionMapper> mapperSupplier, Clock clock) {
    this.mapperSupplier = mapperSupplier;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public ActivityContributionResponse getContributions(long userId, String timezone) {
    ZoneId zoneId = parseTimezone(timezone);
    LocalDate to = LocalDate.now(clock.withZone(zoneId));
    LocalDate from = to.minusDays(LOOKBACK_DAYS - 1L);
    Instant fromInclusive = from.atStartOfDay(zoneId).toInstant();
    Instant toExclusive = to.plusDays(1).atStartOfDay(zoneId).toInstant();

    Map<LocalDate, Integer> counts = countsByDate(
        mapper().findDailyCounts(userId, zoneId.getId(), fromInclusive, toExclusive));
    List<ActivityContributionDayResponse> days = calendarDays(from, to, counts);
    return new ActivityContributionResponse(
        zoneId.getId(),
        from,
        to,
        totalCount(from, to, counts),
        activeDays(from, to, counts),
        currentStreak(from, to, counts),
        longestStreak(from, to, counts),
        days);
  }

  private Map<LocalDate, Integer> countsByDate(List<ActivityContributionRow> rows) {
    Map<LocalDate, Integer> counts = new HashMap<>();
    if (rows == null) {
      return counts;
    }
    rows.forEach(row -> {
      if (row != null && row.activityDate() != null) {
        counts.merge(row.activityDate(), Math.max(0, row.activityCount()), Integer::sum);
      }
    });
    return counts;
  }

  private List<ActivityContributionDayResponse> calendarDays(
      LocalDate from,
      LocalDate to,
      Map<LocalDate, Integer> counts
  ) {
    LocalDate gridStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
    LocalDate gridEnd = to.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
    java.util.ArrayList<ActivityContributionDayResponse> days = new java.util.ArrayList<>();
    for (LocalDate date = gridStart; !date.isAfter(gridEnd); date = date.plusDays(1)) {
      int count = counts.getOrDefault(date, 0);
      days.add(new ActivityContributionDayResponse(date, count, level(count)));
    }
    return List.copyOf(days);
  }

  private long totalCount(LocalDate from, LocalDate to, Map<LocalDate, Integer> counts) {
    long total = 0;
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      total += counts.getOrDefault(date, 0);
    }
    return total;
  }

  private int activeDays(LocalDate from, LocalDate to, Map<LocalDate, Integer> counts) {
    int active = 0;
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      if (counts.getOrDefault(date, 0) > 0) {
        active++;
      }
    }
    return active;
  }

  private int currentStreak(LocalDate from, LocalDate to, Map<LocalDate, Integer> counts) {
    int streak = 0;
    for (LocalDate date = to; !date.isBefore(from) && counts.getOrDefault(date, 0) > 0; date = date.minusDays(1)) {
      streak++;
    }
    return streak;
  }

  private int longestStreak(LocalDate from, LocalDate to, Map<LocalDate, Integer> counts) {
    int longest = 0;
    int current = 0;
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      current = counts.getOrDefault(date, 0) > 0 ? current + 1 : 0;
      longest = Math.max(longest, current);
    }
    return longest;
  }

  private int level(int count) {
    if (count <= 0) {
      return 0;
    }
    if (count <= 2) {
      return 1;
    }
    if (count <= 4) {
      return 2;
    }
    if (count <= 6) {
      return 3;
    }
    return 4;
  }

  private ZoneId parseTimezone(String timezone) {
    String normalized = timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone.trim();
    try {
      return ZoneId.of(normalized);
    } catch (DateTimeException exception) {
      throw new ActivityTimezoneInvalidException();
    }
  }

  private ActivityContributionMapper mapper() {
    ActivityContributionMapper mapper = mapperSupplier.get();
    if (mapper == null) {
      throw new ActivityContributionMapperUnavailableException();
    }
    return mapper;
  }

  public static class ActivityContributionMapperUnavailableException extends RuntimeException {

    public ActivityContributionMapperUnavailableException() {
      super("Activity contribution mapper is unavailable. Enable the local datasource profile before using activity APIs.");
    }
  }

  public static class ActivityTimezoneInvalidException extends RuntimeException {

    public ActivityTimezoneInvalidException() {
      super("时区参数无效。");
    }
  }
}
