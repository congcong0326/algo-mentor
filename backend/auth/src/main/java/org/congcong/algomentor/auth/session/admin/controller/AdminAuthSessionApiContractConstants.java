package org.congcong.algomentor.auth.session.admin.controller;

/**
 * 管理员会话监控 HTTP 路径与字段契约。
 */
public final class AdminAuthSessionApiContractConstants {

  public static final String ADMIN_AUTH_SESSIONS_BASE_PATH = "/api/admin/auth-sessions";
  public static final String SESSION_REF_PATH = "/{sessionRef}";
  public static final String CURRENT_REVOKE_FORBIDDEN_MESSAGE_KEY =
      "error.auth.session.currentRevokeForbidden";

  private AdminAuthSessionApiContractConstants() {
  }
}
