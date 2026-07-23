package org.congcong.algomentor.auth.session.policy;

/** 会话策略解析来源的低基数指标标签。 */
public enum AuthSessionPolicyResolutionSource {
  POLICY("policy"),
  DEFAULT("default");

  private final String metricTag;

  AuthSessionPolicyResolutionSource(String metricTag) {
    this.metricTag = metricTag;
  }

  public String metricTag() {
    return metricTag;
  }
}
