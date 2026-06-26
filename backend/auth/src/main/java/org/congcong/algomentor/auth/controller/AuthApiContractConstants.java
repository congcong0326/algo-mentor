package org.congcong.algomentor.auth.controller;

/**
 * 认证模块对外 HTTP 契约路径和错误码。
 */
public final class AuthApiContractConstants {

  /**
   * 认证接口根路径。
   */
  public static final String AUTH_API_BASE_PATH = "/api/auth";

  /**
   * 当前登录用户信息路径。
   */
  public static final String ME_PATH = "/me";

  /**
   * 邮箱密码注册路径。
   */
  public static final String REGISTER_PATH = "/register";

  /**
   * 邮箱密码登录路径。
   */
  public static final String LOGIN_PATH = "/login";

  /**
   * 当前请求没有可用登录用户时返回的错误码。
   */
  public static final String AUTH_UNAUTHENTICATED_CODE = "AUTH_UNAUTHENTICATED";

  private AuthApiContractConstants() {
  }
}
