package org.congcong.algomentor.auth.session.policy;

/** 认证 Session 中保存的用户会话策略快照属性名。 */
public final class AuthSessionAttributeNames {

  /** 不随访问延长的 UTC epoch milliseconds 硬截止。 */
  public static final String ABSOLUTE_EXPIRES_AT_EPOCH_MILLIS =
      "AUTH_SESSION_ABSOLUTE_EXPIRES_AT_EPOCH_MILLIS";

  /** 命中的通用策略 ID；默认策略快照不保存该属性。 */
  public static final String POLICY_ID = "AUTH_SESSION_POLICY_ID";

  /** 命中的通用策略版本；默认策略快照不保存该属性。 */
  public static final String POLICY_VERSION = "AUTH_SESSION_POLICY_VERSION";

  private AuthSessionAttributeNames() {
  }
}
