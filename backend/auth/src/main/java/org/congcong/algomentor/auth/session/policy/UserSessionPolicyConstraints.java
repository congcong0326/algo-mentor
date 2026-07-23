package org.congcong.algomentor.auth.session.policy;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Set;

/** 用户会话策略数值边界和 Session 超时换算规则。 */
public final class UserSessionPolicyConstraints {

  public static final String MAX_SESSIONS_FIELD = "maxSessions";
  public static final String ABSOLUTE_TIMEOUT_SECONDS_FIELD = "absoluteTimeoutSeconds";
  public static final int MIN_MAX_SESSIONS = 1;
  public static final int MAX_MAX_SESSIONS = Integer.MAX_VALUE;
  public static final long MIN_ABSOLUTE_TIMEOUT_SECONDS = 1L;
  public static final long MAX_ABSOLUTE_TIMEOUT_SECONDS = Integer.MAX_VALUE;
  private static final Set<String> CONTENT_FIELDS = Set.of(
      MAX_SESSIONS_FIELD,
      ABSOLUTE_TIMEOUT_SECONDS_FIELD);

  private UserSessionPolicyConstraints() {
  }

  public static void validate(int maxSessions, long absoluteTimeoutSeconds) {
    if (maxSessions < MIN_MAX_SESSIONS || maxSessions > MAX_MAX_SESSIONS) {
      throw new IllegalArgumentException(MAX_SESSIONS_FIELD + " must be a positive integer.");
    }
    if (absoluteTimeoutSeconds < MIN_ABSOLUTE_TIMEOUT_SECONDS
        || absoluteTimeoutSeconds > MAX_ABSOLUTE_TIMEOUT_SECONDS) {
      throw new IllegalArgumentException(
          ABSOLUTE_TIMEOUT_SECONDS_FIELD + " must be positive and fit Servlet Session seconds.");
    }
  }

  /** 校验策略原始 JSON，拒绝未知字段、缺失字段和隐式类型转换。 */
  public static void validateContent(JsonNode content, UserSessionPolicy policy) {
    if (content == null || !content.isObject() || content.size() != CONTENT_FIELDS.size()) {
      throw new IllegalArgumentException("User session policy content must contain exactly the supported fields.");
    }
    java.util.Iterator<String> fieldNames = content.fieldNames();
    while (fieldNames.hasNext()) {
      if (!CONTENT_FIELDS.contains(fieldNames.next())) {
        throw new IllegalArgumentException("User session policy content contains an unsupported field.");
      }
    }
    JsonNode maxSessions = content.get(MAX_SESSIONS_FIELD);
    JsonNode absoluteTimeout = content.get(ABSOLUTE_TIMEOUT_SECONDS_FIELD);
    if (maxSessions == null || !maxSessions.isIntegralNumber() || !maxSessions.canConvertToInt()
        || absoluteTimeout == null || !absoluteTimeout.isIntegralNumber()
        || !absoluteTimeout.canConvertToLong()) {
      throw new IllegalArgumentException("User session policy content fields must be integral numbers.");
    }
    if (maxSessions.intValue() != policy.maxSessions()
        || absoluteTimeout.longValue() != policy.absoluteTimeoutSeconds()) {
      throw new IllegalArgumentException("User session policy content values cannot be coerced.");
    }
  }

  public static Duration absoluteTimeout(long absoluteTimeoutSeconds) {
    if (absoluteTimeoutSeconds < MIN_ABSOLUTE_TIMEOUT_SECONDS
        || absoluteTimeoutSeconds > MAX_ABSOLUTE_TIMEOUT_SECONDS) {
      throw new IllegalArgumentException(
          ABSOLUTE_TIMEOUT_SECONDS_FIELD + " must be positive and fit Servlet Session seconds.");
    }
    return Duration.ofSeconds(absoluteTimeoutSeconds);
  }

  public static int toServletSessionSeconds(Duration timeout) {
    if (timeout == null || timeout.isNegative() || timeout.toSeconds() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Session timeout must fit Servlet Session seconds.");
    }
    return Math.toIntExact(timeout.toSeconds());
  }
}
