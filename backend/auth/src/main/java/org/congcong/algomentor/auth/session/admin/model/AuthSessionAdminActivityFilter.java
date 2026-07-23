package org.congcong.algomentor.auth.session.admin.model;

import java.util.Locale;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminErrorCode;
import org.congcong.algomentor.auth.session.admin.service.AuthSessionAdminException;

/**
 * 管理会话列表的活动状态筛选契约。
 */
public enum AuthSessionAdminActivityFilter {
  ALL,
  ACTIVE,
  IDLE;

  public static AuthSessionAdminActivityFilter from(String value) {
    if (value == null || value.isBlank()) {
      return ALL;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new AuthSessionAdminException(
          AuthSessionAdminErrorCode.AUTH_SESSION_QUERY_INVALID,
          "会话活动状态不合法。");
    }
  }
}
