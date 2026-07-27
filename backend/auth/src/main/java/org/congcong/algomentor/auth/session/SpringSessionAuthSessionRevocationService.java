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
  public boolean revokeSession(String sessionId) {
    if (sessionId == null || sessionId.isBlank() || sessionRepository.findById(sessionId) == null) {
      return false;
    }
    sessionRepository.deleteById(sessionId);
    return true;
  }

  @Override
  public int revokeSessionsForUser(long userId) {
    Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(Long.toString(userId));
    sessions.keySet().forEach(sessionRepository::deleteById);
    return sessions.size();
  }

  @Override
  public int revokeOtherSessionsForUser(long userId, String currentSessionId) {
    Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(Long.toString(userId));
    int revoked = 0;
    for (String sessionId : sessions.keySet()) {
      if (!sessionId.equals(currentSessionId)) {
        sessionRepository.deleteById(sessionId);
        revoked++;
      }
    }
    return revoked;
  }
}
