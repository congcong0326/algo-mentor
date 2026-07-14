package org.congcong.algomentor.api.controller.admin.ai;

/** 阶段二 AI 治理管理员 API 的稳定路径与错误码契约。 */
public final class AdminAiApiContractConstants {

  public static final String ADMIN_AI_BASE_PATH = "/api/admin/ai";
  public static final String SETTINGS_PATH = "/settings";
  public static final String MODEL_PRICES_PATH = "/model-prices";
  public static final String MODEL_PRICE_ID_PATH = "/model-prices/{priceId}";
  public static final String USAGE_SUMMARY_PATH = "/usage/summary";
  public static final String USAGE_BY_USER_PATH = "/usage/by-user";
  public static final String USAGE_BY_MODEL_PATH = "/usage/by-model";
  public static final String USAGE_BY_SOURCE_PATH = "/usage/by-source";
  public static final String ADMIN_USERS_BASE_PATH = "/api/admin/users";
  public static final String USER_AI_POLICY_PATH = "/{userId}/ai-policy";

  public static final String AI_GLOBALLY_DISABLED = "AI_GLOBALLY_DISABLED";
  public static final String AI_USER_DISABLED = "AI_USER_DISABLED";
  public static final String AI_RUNTIME_SETTINGS_INVALID = "AI_RUNTIME_SETTINGS_INVALID";
  public static final String AI_USER_POLICY_INVALID = "AI_USER_POLICY_INVALID";
  public static final String AI_MODEL_PRICE_NOT_FOUND = "AI_MODEL_PRICE_NOT_FOUND";
  public static final String AI_MODEL_PRICE_ALREADY_EXISTS = "AI_MODEL_PRICE_ALREADY_EXISTS";
  public static final String AI_MODEL_PRICE_INVALID = "AI_MODEL_PRICE_INVALID";
  public static final String AI_USAGE_DATE_RANGE_INVALID = "AI_USAGE_DATE_RANGE_INVALID";
  public static final String AI_USAGE_QUERY_INVALID = "AI_USAGE_QUERY_INVALID";
  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String REQUEST_BODY_INVALID = "REQUEST_BODY_INVALID";

  private AdminAiApiContractConstants() {
  }
}
