package org.congcong.algomentor.auth.session.policy;

/** 用户会话策略的跨模块类型和默认值契约。 */
public final class AuthSessionPolicyConstants {

  public static final String TYPE_CODE = "auth.user-session.v1";
  public static final int DEFAULT_MAX_SESSIONS = 2;
  public static final long DEFAULT_ABSOLUTE_TIMEOUT_SECONDS = 86_400L;

  private AuthSessionPolicyConstants() {
  }
}
