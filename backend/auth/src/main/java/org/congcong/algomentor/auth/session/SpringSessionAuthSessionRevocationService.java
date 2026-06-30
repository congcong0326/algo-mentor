package org.congcong.algomentor.auth.session;

import java.util.Map;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

public class SpringSessionAuthSessionRevocationService implements AuthSessionRevocationService {

  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  public SpringSessionAuthSessionRevocationService(
      FindByIndexNameSessionRepository<? extends Session> sessionRepository
  ) {
    this.sessionRepository = sessionRepository;
  }

  @Override
  public int revokeSessionsForUser(long userId) {
    Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(Long.toString(userId));
    sessions.keySet().forEach(sessionRepository::deleteById);
    return sessions.size();
  }
}
