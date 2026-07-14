package org.congcong.algomentor.ai.governance.adminquery;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.congcong.algomentor.ai.governance.model.AiGovernanceAdminException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;

/** 管理员用量查询的统一筛选条件与日期边界。 */
public record AiUsageQuery(
    LocalDate from,
    LocalDate to,
    Instant fromAt,
    Instant toExclusive,
    ZoneId quotaZone,
    Long userId,
    String provider,
    String model,
    String purpose,
    String source
) {

  public static final int MAX_DAYS = 90;

  public static AiUsageQuery of(
      LocalDate from,
      LocalDate to,
      ZoneId quotaZone,
      Long userId,
      String provider,
      String model,
      String purpose,
      String source
  ) {
    ZoneId effectiveZone = quotaZone == null ? ZoneId.of("UTC") : quotaZone;
    LocalDate today = LocalDate.now(effectiveZone);
    LocalDate effectiveFrom = from == null ? today : from;
    LocalDate effectiveTo = to == null ? today : to;
    if (effectiveFrom.isAfter(effectiveTo)
        || ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) >= MAX_DAYS) {
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_USAGE_DATE_RANGE_INVALID,
          "AI usage date range is invalid.");
    }
    if (userId != null && userId < 1) {
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_USAGE_QUERY_INVALID,
          "AI usage user id is invalid.");
    }
    return new AiUsageQuery(
        effectiveFrom,
        effectiveTo,
        effectiveFrom.atStartOfDay(effectiveZone).toInstant(),
        effectiveTo.plusDays(1).atStartOfDay(effectiveZone).toInstant(),
        effectiveZone,
        userId,
        normalizeProvider(provider),
        normalizeExact(model),
        normalizeExact(purpose),
        normalizeExact(source));
  }

  private static String normalizeProvider(String value) {
    String normalized = normalizeExact(value);
    return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
  }

  private static String normalizeExact(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
