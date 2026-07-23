package org.congcong.algomentor.auth.session.policy;

/** 用户会话策略控制链的低基数观测端口。 */
public interface AuthSessionPolicyMetrics {

  void recordResolution(AuthSessionPolicyResolutionSource source, boolean success);

  void recordEvictions(int count);

  void recordAbsoluteExpiration();

  void recordFailure(AuthSessionPolicyFailureOperation operation);
}
