package org.congcong.algomentor.common.admin.audit;

/**
 * 审计 metadata 允许使用的低敏键，禁止自由文本键进入持久化审计。
 */
public enum AdminAuditMetadataKey {
  EMAIL_ALLOWLIST_ENABLED("emailAllowlistEnabled"),
  ADDED_COUNT("addedCount"),
  EXISTING_COUNT("existingCount"),
  INVALID_COUNT("invalidCount"),
  REVOKED_SESSION_COUNT("revokedSessionCount"),
  SESSION_REVOCATION_SUCCEEDED("sessionRevocationSucceeded"),
  GLOBAL_AI_ENABLED("globalAiEnabled"),
  DEFAULT_DAILY_REQUEST_LIMIT("defaultDailyRequestLimit"),
  AI_ENABLED_OVERRIDE("aiEnabledOverride"),
  DAILY_REQUEST_LIMIT_OVERRIDE("dailyRequestLimitOverride"),
  PROVIDER("provider"),
  MODEL("model"),
  INPUT_PRICE_PER_MILLION("inputPricePerMillion"),
  CACHED_INPUT_PRICE_PER_MILLION("cachedInputPricePerMillion"),
  OUTPUT_PRICE_PER_MILLION("outputPricePerMillion"),
  COST_MULTIPLIER("costMultiplier"),
  PRICE_ENABLED("priceEnabled"),
  GROUP_CODE("groupCode"),
  USER_ID("userId"),
  USER_COUNT("userCount"),
  UPDATED_COUNT("updatedCount"),
  FAILED_COUNT("failedCount"),
  REMOVED_MEMBERSHIP_COUNT("removedMembershipCount"),
  REMOVED("removed"),
  ERROR_CODE("errorCode");

  private final String value;

  AdminAuditMetadataKey(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
