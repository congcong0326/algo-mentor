package org.congcong.algomentor.identity.controller;

public final class AdminUserApiContractConstants {

  public static final String ADMIN_USERS_BASE_PATH = "/api/admin/users";
  public static final String USER_ID_PATH = "/{userId}";
  public static final String STATUS_PATH = "/{userId}/status";

  public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
  public static final String USER_STATUS_CONFLICT = "USER_STATUS_CONFLICT";
  public static final String USER_SELF_OPERATION_FORBIDDEN = "USER_SELF_OPERATION_FORBIDDEN";
  public static final String USER_STATUS_INVALID = "USER_STATUS_INVALID";

  private AdminUserApiContractConstants() {
  }
}
