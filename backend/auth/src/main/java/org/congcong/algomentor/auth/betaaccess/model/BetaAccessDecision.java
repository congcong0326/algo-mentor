package org.congcong.algomentor.auth.betaaccess.model;

public enum BetaAccessDecision {
  ALLOWED_ALLOWLIST_DISABLED(true),
  ALLOWED_TRUSTED_ADMIN(true),
  ALLOWED_EMAIL(true),
  DENIED_INVALID_EMAIL(false),
  DENIED_EMAIL_NOT_ALLOWED(false);

  private final boolean allowed;

  BetaAccessDecision(boolean allowed) {
    this.allowed = allowed;
  }

  public boolean allowed() {
    return allowed;
  }
}
