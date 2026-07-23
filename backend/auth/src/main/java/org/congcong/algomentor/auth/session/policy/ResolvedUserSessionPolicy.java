package org.congcong.algomentor.auth.session.policy;

import java.util.Objects;

/** 已在登录时选定的用户会话策略，包含最小必要的会话快照信息。 */
public record ResolvedUserSessionPolicy(
    UserSessionPolicy content,
    Long policyId,
    Long policyVersion
) {

  public ResolvedUserSessionPolicy {
    content = Objects.requireNonNull(content, "content must not be null");
    if ((policyId == null) != (policyVersion == null)) {
      throw new IllegalArgumentException("policyId and policyVersion must both be present or absent.");
    }
    if (policyId != null && (policyId < 1 || policyVersion < 1)) {
      throw new IllegalArgumentException("policyId and policyVersion must be positive.");
    }
  }

  public static ResolvedUserSessionPolicy defaults() {
    return new ResolvedUserSessionPolicy(
        new UserSessionPolicy(
            AuthSessionPolicyConstants.DEFAULT_MAX_SESSIONS,
            AuthSessionPolicyConstants.DEFAULT_ABSOLUTE_TIMEOUT_SECONDS),
        null,
        null);
  }

  public boolean usesDefault() {
    return policyId == null;
  }
}
