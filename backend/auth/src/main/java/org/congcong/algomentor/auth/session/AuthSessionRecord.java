package org.congcong.algomentor.auth.session;

import java.time.Instant;
import java.util.Objects;

/** 认证模块内部使用的 Spring Session 查询结果，不向 HTTP 响应暴露 Session ID。 */
public record AuthSessionRecord(
    String sessionId,
    Instant creationTime,
    boolean expired
) {

  public AuthSessionRecord {
    sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
    creationTime = Objects.requireNonNull(creationTime, "creationTime must not be null");
  }
}
