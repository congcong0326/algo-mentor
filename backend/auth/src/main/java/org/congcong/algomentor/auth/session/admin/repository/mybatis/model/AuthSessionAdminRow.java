package org.congcong.algomentor.auth.session.admin.repository.mybatis.model;

import java.time.Instant;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionActivity;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminRecord;
import org.congcong.algomentor.identity.model.AuthUserStatus;

public record AuthSessionAdminRow(
    String sessionRef,
    String sessionId,
    long userId,
    long creationTime,
    long lastAccessTime,
    long expiryTime,
    String email,
    String displayName,
    String userStatus
) {

  /**
   * 适配 MyBatis/JDBC 对 PostgreSQL BIGINT 使用 {@link Long} 的构造器解析。
   */
  public AuthSessionAdminRow(
      String sessionRef,
      String sessionId,
      Long userId,
      Long creationTime,
      Long lastAccessTime,
      Long expiryTime,
      String email,
      String displayName,
      String userStatus
  ) {
    this(
        sessionRef,
        sessionId,
        userId.longValue(),
        creationTime.longValue(),
        lastAccessTime.longValue(),
        expiryTime.longValue(),
        email,
        displayName,
        userStatus);
  }

  public AuthSessionAdminRecord toDomain(long activeSinceEpochMillis) {
    return new AuthSessionAdminRecord(
        sessionRef,
        sessionId,
        userId,
        email,
        displayName,
        userStatus == null ? null : AuthUserStatus.valueOf(userStatus),
        Instant.ofEpochMilli(creationTime),
        Instant.ofEpochMilli(lastAccessTime),
        Instant.ofEpochMilli(expiryTime),
        lastAccessTime >= activeSinceEpochMillis ? AuthSessionActivity.ACTIVE : AuthSessionActivity.IDLE,
        false);
  }
}
