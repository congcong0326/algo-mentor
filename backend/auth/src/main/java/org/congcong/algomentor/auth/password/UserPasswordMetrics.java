package org.congcong.algomentor.auth.password;

import org.congcong.algomentor.auth.security.AuthSessionAuthenticationMethod;

/**
 * 用户主动设置或修改密码的低基数指标端口。
 */
public interface UserPasswordMetrics {

  void recordSuccess(AuthSessionAuthenticationMethod method, UserPasswordUpdateOperation operation);

  void recordFailure(AuthSessionAuthenticationMethod method, UserPasswordErrorCode code);

  void recordSessionRevocations(int revokedSessionCount);
}
