package org.congcong.algomentor.auth.session.policy;

import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.session.AuthSessionRecord;
import org.congcong.algomentor.auth.session.AuthSessionRepository;
import org.congcong.algomentor.auth.session.AuthSessionRevocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 在新登录完成时写入策略快照并按创建时间收敛该账号的有效会话数。 */
public class AuthSessionPolicyLoginService {

  private static final Logger log = LoggerFactory.getLogger(AuthSessionPolicyLoginService.class);

  private final AuthSessionPolicyResolver resolver;
  private final AuthSessionRepository sessionRepository;
  private final AuthSessionRevocationService revocationService;
  private final AuthSessionPolicyMetrics metrics;
  private final Clock clock;
  private final Duration globalIdleTimeout;
  private final boolean available;

  public AuthSessionPolicyLoginService(
      AuthSessionPolicyResolver resolver,
      AuthSessionRepository sessionRepository,
      AuthSessionRevocationService revocationService,
      AuthSessionPolicyMetrics metrics,
      Clock clock,
      AuthProperties properties
  ) {
    this.resolver = resolver;
    this.sessionRepository = sessionRepository;
    this.revocationService = revocationService;
    this.metrics = metrics;
    this.clock = clock;
    this.globalIdleTimeout = properties.getSessionTimeout();
    this.available = true;
  }

  private AuthSessionPolicyLoginService(AuthSessionPolicyMetrics metrics) {
    this.resolver = null;
    this.sessionRepository = null;
    this.revocationService = null;
    this.metrics = metrics;
    this.clock = null;
    this.globalIdleTimeout = null;
    this.available = false;
  }

  public static AuthSessionPolicyLoginService unavailable(AuthSessionPolicyMetrics metrics) {
    return new AuthSessionPolicyLoginService(metrics);
  }

  public void apply(long userId, HttpSession currentSession) {
    if (!available) {
      metrics.recordResolution(AuthSessionPolicyResolutionSource.POLICY, false);
      metrics.recordFailure(AuthSessionPolicyFailureOperation.RESOLVE);
      invalidateCurrentSession(currentSession);
      throw unavailableException(null);
    }
    if (currentSession == null) {
      metrics.recordFailure(AuthSessionPolicyFailureOperation.SNAPSHOT);
      throw unavailableException(new IllegalStateException("Current login session is missing."));
    }

    try {
      ResolvedUserSessionPolicy policy = resolver.resolve(userId);
      Instant now = Instant.now(clock);
      writeSnapshot(currentSession, policy, now);
      List<AuthSessionRecord> otherSessions = findValidOtherSessions(userId, currentSession.getId());
      int revokeCount = Math.max(0, otherSessions.size() + 1 - policy.content().maxSessions());
      revokeOldestSessions(otherSessions, revokeCount);
      metrics.recordEvictions(revokeCount);
      log.info(
          "Applied auth session policy. typeCode={} policyId={} policyVersion={} evictedSessionCount={}",
          AuthSessionPolicyConstants.TYPE_CODE,
          policy.policyId(),
          policy.policyVersion(),
          revokeCount);
    } catch (AuthSessionPolicyException exception) {
      invalidateCurrentSession(currentSession);
      throw exception;
    } catch (RuntimeException exception) {
      metrics.recordFailure(AuthSessionPolicyFailureOperation.SNAPSHOT);
      invalidateCurrentSession(currentSession);
      throw unavailableException(exception);
    }
  }

  private void writeSnapshot(
      HttpSession session,
      ResolvedUserSessionPolicy policy,
      Instant now
  ) {
    try {
      Instant absoluteExpiresAt = now.plus(
          UserSessionPolicyConstraints.absoluteTimeout(policy.content().absoluteTimeoutSeconds()));
      session.setAttribute(
          AuthSessionAttributeNames.ABSOLUTE_EXPIRES_AT_EPOCH_MILLIS,
          absoluteExpiresAt.toEpochMilli());
      writeOptionalSnapshotAttribute(session, AuthSessionAttributeNames.POLICY_ID, policy.policyId());
      writeOptionalSnapshotAttribute(session, AuthSessionAttributeNames.POLICY_VERSION, policy.policyVersion());
      tightenIdleTimeout(session, now, absoluteExpiresAt);
    } catch (RuntimeException exception) {
      metrics.recordFailure(AuthSessionPolicyFailureOperation.SNAPSHOT);
      throw unavailableException(exception);
    }
  }

  private List<AuthSessionRecord> findValidOtherSessions(long userId, String currentSessionId) {
    try {
      return sessionRepository.findByUserId(userId).stream()
          .filter(session -> !session.expired())
          .filter(session -> !session.sessionId().equals(currentSessionId))
          .sorted(Comparator.comparing(AuthSessionRecord::creationTime)
              .thenComparing(AuthSessionRecord::sessionId))
          .toList();
    } catch (RuntimeException exception) {
      metrics.recordFailure(AuthSessionPolicyFailureOperation.QUERY);
      throw unavailableException(exception);
    }
  }

  private void revokeOldestSessions(List<AuthSessionRecord> sessions, int revokeCount) {
    try {
      for (int index = 0; index < revokeCount; index++) {
        if (!revocationService.revokeSession(sessions.get(index).sessionId())) {
          throw new IllegalStateException("Session selected for policy eviction is unavailable.");
        }
      }
    } catch (RuntimeException exception) {
      metrics.recordFailure(AuthSessionPolicyFailureOperation.REVOKE);
      throw unavailableException(exception);
    }
  }

  private void tightenIdleTimeout(HttpSession session, Instant now, Instant absoluteExpiresAt) {
    Duration remaining = Duration.between(now, absoluteExpiresAt);
    Duration effectiveTimeout = globalIdleTimeout.compareTo(remaining) <= 0
        ? globalIdleTimeout
        : remaining;
    session.setMaxInactiveInterval(UserSessionPolicyConstraints.toServletSessionSeconds(effectiveTimeout));
  }

  private static void writeOptionalSnapshotAttribute(HttpSession session, String name, Long value) {
    if (value == null) {
      session.removeAttribute(name);
    } else {
      session.setAttribute(name, value);
    }
  }

  private void invalidateCurrentSession(HttpSession session) {
    if (session == null) {
      return;
    }
    try {
      session.invalidate();
    } catch (IllegalStateException exception) {
      log.debug("Current auth session was already invalidated after session policy failure.");
    }
  }

  private static AuthSessionPolicyException unavailableException(Throwable cause) {
    return cause == null
        ? new AuthSessionPolicyException(
            AuthSessionPolicyErrorCode.AUTH_SESSION_POLICY_UNAVAILABLE,
            "用户会话策略暂不可用，请稍后重试。")
        : new AuthSessionPolicyException(
            AuthSessionPolicyErrorCode.AUTH_SESSION_POLICY_UNAVAILABLE,
            "用户会话策略暂不可用，请稍后重试。",
            cause);
  }
}
