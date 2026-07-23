package org.congcong.algomentor.auth.session;

public interface AuthSessionRevocationService {

  /**
   * 删除单个 Spring Session。返回 {@code false} 表示目标已经不存在。
   */
  default boolean revokeSession(String sessionId) {
    throw new UnsupportedOperationException("Single session revocation is not supported.");
  }

  int revokeSessionsForUser(long userId);
}
