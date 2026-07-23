package org.congcong.algomentor.auth.session.admin.service;

public enum AuthSessionAdminRevocationOutcome {
  REVOKED("revoked"),
  ALREADY_OFFLINE("already_offline"),
  REJECTED_CURRENT("rejected_current"),
  FAILED("failed");

  private final String metricTag;

  AuthSessionAdminRevocationOutcome(String metricTag) {
    this.metricTag = metricTag;
  }

  public String metricTag() {
    return metricTag;
  }
}
