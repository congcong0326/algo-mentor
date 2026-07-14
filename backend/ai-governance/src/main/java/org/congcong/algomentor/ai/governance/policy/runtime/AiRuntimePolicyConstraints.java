package org.congcong.algomentor.ai.governance.policy.runtime;

/** AI 入口请求每日额度的统一业务边界。 */
public final class AiRuntimePolicyConstraints {

  public static final int MIN_DAILY_REQUEST_LIMIT = 1;
  public static final int MAX_DAILY_REQUEST_LIMIT = 10_000;

  private AiRuntimePolicyConstraints() {
  }

  public static boolean isValidDailyRequestLimit(Integer value) {
    return value != null
        && value >= MIN_DAILY_REQUEST_LIMIT
        && value <= MAX_DAILY_REQUEST_LIMIT;
  }
}
