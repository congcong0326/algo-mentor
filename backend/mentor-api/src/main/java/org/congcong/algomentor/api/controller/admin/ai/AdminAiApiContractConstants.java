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
  public static final String PROVIDER_TYPES_PATH = "/provider-types";
  public static final String PROVIDERS_PATH = "/providers";
  public static final String PROVIDER_ID_PATH = "/providers/{providerInstanceId}";
  public static final String PROVIDER_MODELS_PATH = "/providers/{providerInstanceId}/models";
  public static final String MODEL_ID_PATH = "/models/{modelId}";
  public static final String MODEL_ROUTING_SCENARIOS_PATH = "/model-routing/scenarios";
  public static final String MODEL_ROUTING_EFFECTIVE_PATH =
      "/model-routing/scenarios/{scenarioCode}/effective";
  public static final String AUDIT_RUNS_PATH = "/audit/runs";
  public static final String AUDIT_RUN_PATH = "/audit/runs/{runId}";
  public static final String AUDIT_STEP_PATH = "/audit/runs/{runId}/steps/{stepIndex}";
  public static final String AUDIT_TOOL_RESULT_PATH = "/audit/runs/{runId}/tool-results/{toolCallId}";
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
  public static final String AI_MODEL_ROUTE_NOT_CONFIGURED = "AI_MODEL_ROUTE_NOT_CONFIGURED";
  public static final String AI_MODEL_UNAVAILABLE = "AI_MODEL_UNAVAILABLE";
  public static final String AI_PROVIDER_TYPE_NOT_SUPPORTED = "AI_PROVIDER_TYPE_NOT_SUPPORTED";
  public static final String AI_PROVIDER_CONFIG_INVALID = "AI_PROVIDER_CONFIG_INVALID";
  public static final String AI_PROVIDER_NAME_ALREADY_EXISTS = "AI_PROVIDER_NAME_ALREADY_EXISTS";
  public static final String AI_MODEL_ALREADY_EXISTS = "AI_MODEL_ALREADY_EXISTS";
  public static final String AI_PROVIDER_NOT_FOUND = "AI_PROVIDER_NOT_FOUND";
  public static final String AI_MODEL_NOT_FOUND = "AI_MODEL_NOT_FOUND";
  public static final String AI_MODEL_INVALID = "AI_MODEL_INVALID";
  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String REQUEST_BODY_INVALID = "REQUEST_BODY_INVALID";

  private AdminAiApiContractConstants() {
  }
}
