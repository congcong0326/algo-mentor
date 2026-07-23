package org.congcong.algomentor.auth.session;

import java.util.List;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

/** 基于 Spring Session 用户索引的认证 Session 查询适配器。 */
public class SpringSessionAuthSessionRepository implements AuthSessionRepository {

  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  public SpringSessionAuthSessionRepository(
      FindByIndexNameSessionRepository<? extends Session> sessionRepository
  ) {
    this.sessionRepository = sessionRepository;
  }

  @Override
  public List<AuthSessionRecord> findByUserId(long userId) {
    if (userId < 1) {
      throw new IllegalArgumentException("userId must be positive.");
    }
    return sessionRepository.findByPrincipalName(Long.toString(userId)).values().stream()
        .map(session -> new AuthSessionRecord(
            session.getId(), session.getCreationTime(), session.isExpired()))
        .toList();
  }
}
