package org.congcong.algomentor.auth.session;

import org.congcong.algomentor.identity.model.AuthUserStatus;

public interface AuthSessionMetrics {

  void recordSuccess(AuthUserStatus status, int revokedSessions);

  void recordFailure(AuthUserStatus status);
}
