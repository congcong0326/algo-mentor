package org.congcong.algomentor.auth.password;

import org.congcong.algomentor.auth.security.AuthSessionAuthenticationMethod;

public class NoopUserPasswordMetrics implements UserPasswordMetrics {

  @Override
  public void recordSuccess(AuthSessionAuthenticationMethod method, UserPasswordUpdateOperation operation) {
  }

  @Override
  public void recordFailure(AuthSessionAuthenticationMethod method, UserPasswordErrorCode code) {
  }

  @Override
  public void recordSessionRevocations(int revokedSessionCount) {
  }
}
