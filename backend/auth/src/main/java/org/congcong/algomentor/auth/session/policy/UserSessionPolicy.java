package org.congcong.algomentor.auth.session.policy;

/** 管理员写入通用策略内容的强类型表示。 */
public record UserSessionPolicy(
    int maxSessions,
    long absoluteTimeoutSeconds
) {

  public UserSessionPolicy {
    UserSessionPolicyConstraints.validate(maxSessions, absoluteTimeoutSeconds);
  }
}
