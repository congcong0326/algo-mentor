package org.congcong.algomentor.api.controller.admin.ai;

import java.time.LocalDate;
import org.congcong.algomentor.ai.governance.adminquery.AiUsageQuery;
import org.congcong.algomentor.ai.governance.model.AiGovernanceAdminException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.springframework.security.core.Authentication;

/** 管理员 AI API 的认证解析与查询参数归一化。 */
final class AdminAiRequestSupport {

  private AdminAiRequestSupport() {
  }

  static long requireOperatorId(Authentication authentication) {
    if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
      throw invalid("Unable to resolve authenticated administrator.");
    }
    try {
      long userId = Long.parseLong(authentication.getName());
      if (userId < 1) {
        throw invalid("Unable to resolve authenticated administrator.");
      }
      return userId;
    } catch (NumberFormatException exception) {
      throw invalid("Unable to resolve authenticated administrator.");
    }
  }

  static AiUsageQuery usageQuery(
      String from,
      String to,
      Long userId,
      String provider,
      String model,
      String purpose,
      String source,
      AiGovernanceProperties properties
  ) {
    validateEnum(purpose, AiPurpose.class, "purpose");
    validateEnum(source, AiRunSource.class, "source");
    try {
      return AiUsageQuery.of(
          parseDate(from),
          parseDate(to),
          properties.getQuotaZone(),
          userId,
          provider,
          model,
          blankToNull(purpose),
          blankToNull(source));
    } catch (AiGovernanceAdminException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_USAGE_QUERY_INVALID,
          "AI usage query is invalid.",
          exception);
    }
  }

  private static LocalDate parseDate(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (RuntimeException exception) {
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_USAGE_DATE_RANGE_INVALID,
          "AI usage date range is invalid.",
          exception);
    }
  }

  private static <E extends Enum<E>> void validateEnum(String value, Class<E> type, String field) {
    String normalized = blankToNull(value);
    if (normalized == null) {
      return;
    }
    try {
      Enum.valueOf(type, normalized);
    } catch (IllegalArgumentException exception) {
      throw new AiGovernanceAdminException(
          AiGovernanceErrorCode.AI_USAGE_QUERY_INVALID,
          "AI usage " + field + " is invalid.",
          exception);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static AiGovernanceAdminException invalid(String message) {
    return new AiGovernanceAdminException(AiGovernanceErrorCode.AI_RUNTIME_SETTINGS_INVALID, message);
  }
}
