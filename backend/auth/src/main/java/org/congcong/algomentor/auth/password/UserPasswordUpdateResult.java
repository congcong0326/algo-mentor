package org.congcong.algomentor.auth.password;

/**
 * 用户密码写入后返回给 API 的低敏感结果。
 */
public record UserPasswordUpdateResult(
    boolean passwordConfigured,
    UserPasswordUpdateOperation operation,
    int revokedSessionCount
) {
  public UserPasswordUpdateResult {
    if (!passwordConfigured) {
      throw new IllegalArgumentException("passwordConfigured must be true after an update.");
    }
    if (operation == null) {
      throw new IllegalArgumentException("operation must not be null.");
    }
    if (revokedSessionCount < 0) {
      throw new IllegalArgumentException("revokedSessionCount must not be negative.");
    }
  }
}
