package org.congcong.algomentor.auth.session.policy;

/** 会话策略控制链失败位置的低基数指标标签。 */
public enum AuthSessionPolicyFailureOperation {
  RESOLVE("resolve"),
  QUERY("query"),
  REVOKE("revoke"),
  SNAPSHOT("snapshot");

  private final String metricTag;

  AuthSessionPolicyFailureOperation(String metricTag) {
    this.metricTag = metricTag;
  }

  public String metricTag() {
    return metricTag;
  }
}
