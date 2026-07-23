package org.congcong.algomentor.auth.session.admin.service;

/**
 * 管理员会话监控 API 的稳定错误码。
 */
public enum AuthSessionAdminErrorCode {
  AUTH_SESSION_QUERY_INVALID,
  AUTH_SESSION_REF_INVALID,
  AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN,
  AUTH_SESSION_MANAGEMENT_UNAVAILABLE,
  AUTH_SESSION_REVOKE_FAILED
}
