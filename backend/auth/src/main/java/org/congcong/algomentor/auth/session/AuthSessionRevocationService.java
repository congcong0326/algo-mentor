package org.congcong.algomentor.auth.session;

public interface AuthSessionRevocationService {

  /**
   * 删除单个 Spring Session。返回 {@code false} 表示目标已经不存在。
   */
  default boolean revokeSession(String sessionId) {
    throw new UnsupportedOperationException("Single session revocation is not supported.");
  }

  /**
   * 吊销用户除当前 Session 外的所有其他 Session。
   */
  default int revokeOtherSessionsForUser(long userId, String currentSessionId) {
    throw new UnsupportedOperationException("Selective session revocation is not supported.");
  }

  int revokeSessionsForUser(long userId);
}
