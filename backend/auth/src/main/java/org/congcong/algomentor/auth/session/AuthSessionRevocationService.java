package org.congcong.algomentor.auth.session;

public interface AuthSessionRevocationService {

  int revokeSessionsForUser(long userId);
}
