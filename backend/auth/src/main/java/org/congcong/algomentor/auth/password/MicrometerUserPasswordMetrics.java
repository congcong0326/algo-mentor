package org.congcong.algomentor.auth.password;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.auth.security.AuthSessionAuthenticationMethod;

public class MicrometerUserPasswordMetrics implements UserPasswordMetrics {

  static final String PASSWORD_UPDATES_TOTAL = "algo_mentor_auth_password_updates_total";
  static final String PASSWORD_UPDATE_FAILURES_TOTAL = "algo_mentor_auth_password_update_failures_total";
  static final String PASSWORD_SESSION_REVOCATIONS_TOTAL = "algo_mentor_auth_password_session_revocations_total";

  private final MeterRegistry registry;

  public MicrometerUserPasswordMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void recordSuccess(AuthSessionAuthenticationMethod method, UserPasswordUpdateOperation operation) {
    Counter.builder(PASSWORD_UPDATES_TOTAL)
        .tag("method", method.name())
        .tag("operation", operation.name())
        .tag("outcome", "success")
        .register(registry)
        .increment();
  }

  @Override
  public void recordFailure(AuthSessionAuthenticationMethod method, UserPasswordErrorCode code) {
    Counter.builder(PASSWORD_UPDATES_TOTAL)
        .tag("method", method.name())
        .tag("operation", "UNKNOWN")
        .tag("outcome", "failure")
        .register(registry)
        .increment();
    Counter.builder(PASSWORD_UPDATE_FAILURES_TOTAL)
        .tag("reason", failureReason(code))
        .register(registry)
        .increment();
  }

  @Override
  public void recordSessionRevocations(int revokedSessionCount) {
    if (revokedSessionCount > 0) {
      Counter.builder(PASSWORD_SESSION_REVOCATIONS_TOTAL).register(registry).increment(revokedSessionCount);
    }
  }

  private static String failureReason(UserPasswordErrorCode code) {
    return switch (code) {
      case AUTH_PASSWORD_REQUEST_INVALID, AUTH_PASSWORD_LOGIN_EMAIL_UNAVAILABLE -> "invalid_request";
      case AUTH_CURRENT_PASSWORD_REQUIRED, AUTH_CURRENT_PASSWORD_INVALID -> "current_password";
      case AUTH_PASSWORD_CHANGED_CONCURRENTLY -> "concurrent";
      case AUTH_PASSWORD_UPDATE_FAILED -> "storage";
      case AUTH_PASSWORD_CHANGE_REQUIRED, AUTH_PASSWORD_UPDATE_NOT_ALLOWED -> "not_allowed";
    };
  }
}
