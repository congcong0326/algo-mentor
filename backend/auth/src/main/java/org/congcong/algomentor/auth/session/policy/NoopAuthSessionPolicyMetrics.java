package org.congcong.algomentor.auth.session.policy;

/** 无 Micrometer Registry 时使用的无操作实现。 */
public class NoopAuthSessionPolicyMetrics implements AuthSessionPolicyMetrics {

  @Override
  public void recordResolution(AuthSessionPolicyResolutionSource source, boolean success) {
  }

  @Override
  public void recordEvictions(int count) {
  }

  @Override
  public void recordAbsoluteExpiration() {
  }

  @Override
  public void recordFailure(AuthSessionPolicyFailureOperation operation) {
  }
}
