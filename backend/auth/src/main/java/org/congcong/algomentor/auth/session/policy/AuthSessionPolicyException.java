package org.congcong.algomentor.auth.session.policy;

/** 用户会话策略解析、快照或淘汰失败时拒绝登录。 */
public class AuthSessionPolicyException extends RuntimeException {

  private final AuthSessionPolicyErrorCode code;

  public AuthSessionPolicyException(AuthSessionPolicyErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public AuthSessionPolicyException(AuthSessionPolicyErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public AuthSessionPolicyErrorCode code() {
    return code;
  }
}
