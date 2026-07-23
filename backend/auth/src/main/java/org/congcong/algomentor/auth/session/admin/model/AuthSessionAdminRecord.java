package org.congcong.algomentor.auth.session.admin.model;

import java.time.Instant;
import org.congcong.algomentor.identity.model.AuthUserStatus;

/**
 * 管理服务内部会话记录；sessionId 仅用于认证模块内的比对和撤销。
 */
public record AuthSessionAdminRecord(
    String sessionRef,
    String sessionId,
    long userId,
    String email,
    String displayName,
    AuthUserStatus userStatus,
    Instant createdAt,
    Instant lastAccessedAt,
    Instant expiresAt,
    AuthSessionActivity activity,
    boolean current
) {

  public AuthSessionAdminRecord withCurrent(boolean current) {
    return new AuthSessionAdminRecord(
        sessionRef,
        sessionId,
        userId,
        email,
        displayName,
        userStatus,
        createdAt,
        lastAccessedAt,
        expiresAt,
        activity,
        current);
  }
}
