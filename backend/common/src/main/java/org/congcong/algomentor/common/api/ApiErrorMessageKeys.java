package org.congcong.algomentor.common.api;

/**
 * HTTP API 错误消息 key 的统一规则。
 */
public final class ApiErrorMessageKeys {

  public static final String PREFIX = "api.error.";

  private ApiErrorMessageKeys() {
  }

  public static String defaultKey(String code) {
    return PREFIX + code;
  }
}
