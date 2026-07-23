package org.congcong.algomentor.auth.session.admin.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.session.AuthSessionRevocationService;
import org.congcong.algomentor.auth.session.admin.controller.model.AdminAuthSessionListQuery;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminActivityFilter;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminPage;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminQuery;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminRecord;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminRevocation;
import org.congcong.algomentor.auth.session.admin.model.AuthSessionAdminSummary;
import org.congcong.algomentor.auth.session.admin.repository.AuthSessionAdminRepository;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminAuditTargetType;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthSessionAdminService {

  public static final int DEFAULT_PAGE_SIZE = 20;
  public static final int MAX_PAGE_SIZE = 100;
  private static final Logger log = LoggerFactory.getLogger(AuthSessionAdminService.class);

  private final AuthSessionAdminRepository repository;
  private final AuthSessionRevocationService revocationService;
  private final AdminOperationAuditRecorder auditRecorder;
  private final AuthSessionAdminMetrics metrics;
  private final Clock clock;
  private final Duration activeWindow;

  public AuthSessionAdminService(
      AuthSessionAdminRepository repository,
      AuthSessionRevocationService revocationService,
      AdminOperationAuditRecorder auditRecorder,
      AuthSessionAdminMetrics metrics,
      Clock clock,
      AuthProperties properties
  ) {
    this.repository = repository;
    this.revocationService = revocationService;
    this.auditRecorder = auditRecorder;
    this.metrics = metrics;
    this.clock = clock;
    this.activeWindow = properties.getSessionMonitoringActiveWindow();
  }

  public AuthSessionAdminPage list(AdminAuthSessionListQuery request, String currentSessionId) {
    long startedAtNanos = System.nanoTime();
    try {
      AuthSessionAdminQuery query = normalizeQuery(request);
      Instant checkedAt = Instant.now(clock);
      Instant activeSince = checkedAt.minus(activeWindow);
      List<AuthSessionAdminRecord> items = repository.findPage(query, checkedAt, activeSince).stream()
          .map(record -> record.withCurrent(record.sessionId().equals(currentSessionId)))
          .toList();
      long total = repository.count(query, checkedAt, activeSince);
      AuthSessionAdminSummary summary = repository.summary(checkedAt, activeSince);
      return new AuthSessionAdminPage(items, total, query.page(), query.pageSize(), summary, checkedAt);
    } catch (AuthSessionAdminException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new AuthSessionAdminException(
          AuthSessionAdminErrorCode.AUTH_SESSION_MANAGEMENT_UNAVAILABLE,
          "会话管理查询暂不可用，请稍后重试。",
          exception);
    } finally {
      metrics.recordQuery(Duration.ofNanos(System.nanoTime() - startedAtNanos));
    }
  }

  public AuthSessionAdminRevocation revoke(String sessionRef, String currentSessionId, long operatorUserId) {
    validateSessionRef(sessionRef);
    if (operatorUserId < 1) {
      throw new AuthSessionAdminException(
          AuthSessionAdminErrorCode.AUTH_SESSION_QUERY_INVALID,
          "无法解析管理员身份。");
    }
    AuthSessionAdminRecord target = findValidTarget(sessionRef);
    if (target == null) {
      metrics.recordRevocation(AuthSessionAdminRevocationOutcome.ALREADY_OFFLINE);
      recordSuccess(operatorUserId, sessionRef, null, false, true);
      return new AuthSessionAdminRevocation(sessionRef, null, false, true);
    }
    if (target.sessionId().equals(currentSessionId)) {
      metrics.recordRevocation(AuthSessionAdminRevocationOutcome.REJECTED_CURRENT);
      recordFailure(operatorUserId, sessionRef, AuthSessionAdminErrorCode.AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN);
      throw new AuthSessionAdminException(
          AuthSessionAdminErrorCode.AUTH_SESSION_CURRENT_REVOKE_FORBIDDEN,
          "不能在会话监控中下线当前会话，请使用退出登录。");
    }
    try {
      boolean revoked = revocationService.revokeSession(target.sessionId());
      AuthSessionAdminRevocationOutcome outcome = revoked
          ? AuthSessionAdminRevocationOutcome.REVOKED
          : AuthSessionAdminRevocationOutcome.ALREADY_OFFLINE;
      metrics.recordRevocation(outcome);
      recordSuccess(operatorUserId, sessionRef, target.userId(), revoked, !revoked);
      return new AuthSessionAdminRevocation(sessionRef, target.userId(), revoked, !revoked);
    } catch (RuntimeException exception) {
      metrics.recordRevocation(AuthSessionAdminRevocationOutcome.FAILED);
      recordFailure(operatorUserId, sessionRef, AuthSessionAdminErrorCode.AUTH_SESSION_REVOKE_FAILED);
      throw new AuthSessionAdminException(
          AuthSessionAdminErrorCode.AUTH_SESSION_REVOKE_FAILED,
          "会话下线失败，请稍后重试。",
          exception);
    }
  }

  private AuthSessionAdminRecord findValidTarget(String sessionRef) {
    try {
      return repository.findValidBySessionRef(sessionRef, Instant.now(clock)).orElse(null);
    } catch (RuntimeException exception) {
      throw new AuthSessionAdminException(
          AuthSessionAdminErrorCode.AUTH_SESSION_MANAGEMENT_UNAVAILABLE,
          "会话管理查询暂不可用，请稍后重试。",
          exception);
    }
  }

  private static AuthSessionAdminQuery normalizeQuery(AdminAuthSessionListQuery request) {
    int requestedPage = request == null ? 1 : request.page();
    int requestedPageSize = request == null ? DEFAULT_PAGE_SIZE : request.pageSize();
    int page = Math.max(1, requestedPage);
    int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedPageSize));
    String keyword = request == null || request.keyword() == null ? "" : request.keyword().trim();
    Long keywordUserId = parseUserIdKeyword(keyword);
    AuthSessionAdminActivityFilter activity = AuthSessionAdminActivityFilter.from(
        request == null ? null : request.activity());
    return new AuthSessionAdminQuery(page, pageSize, keyword, keywordUserId, activity);
  }

  private static Long parseUserIdKeyword(String keyword) {
    if (keyword.isBlank() || !keyword.chars().allMatch(Character::isDigit)) {
      return null;
    }
    try {
      long value = Long.parseLong(keyword);
      return value > 0 ? value : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static void validateSessionRef(String sessionRef) {
    if (sessionRef == null || sessionRef.isBlank()) {
      throw new AuthSessionAdminException(
          AuthSessionAdminErrorCode.AUTH_SESSION_REF_INVALID,
          "会话引用格式不合法。");
    }
    try {
      UUID.fromString(sessionRef);
    } catch (IllegalArgumentException exception) {
      throw new AuthSessionAdminException(
          AuthSessionAdminErrorCode.AUTH_SESSION_REF_INVALID,
          "会话引用格式不合法。");
    }
  }

  private void recordSuccess(
      long operatorUserId,
      String sessionRef,
      Long targetUserId,
      boolean revoked,
      boolean alreadyOffline
  ) {
    Map<AdminAuditMetadataKey, Object> metadata = targetUserId == null
        ? Map.of(
            AdminAuditMetadataKey.SESSION_REVOKED, revoked,
            AdminAuditMetadataKey.SESSION_ALREADY_OFFLINE, alreadyOffline)
        : Map.of(
            AdminAuditMetadataKey.USER_ID, targetUserId,
            AdminAuditMetadataKey.SESSION_REVOKED, revoked,
            AdminAuditMetadataKey.SESSION_ALREADY_OFFLINE, alreadyOffline);
    recordAudit(AdminOperationAuditEvent.success(
        operatorUserId,
        AdminAuditAction.AUTH_SESSION_REVOKE,
        AdminAuditTargetType.AUTH_SESSION,
        sessionRef,
        metadata), operatorUserId, targetUserId);
  }

  private void recordFailure(long operatorUserId, String sessionRef, AuthSessionAdminErrorCode code) {
    recordAudit(AdminOperationAuditEvent.failure(
        operatorUserId,
        AdminAuditAction.AUTH_SESSION_REVOKE,
        AdminAuditTargetType.AUTH_SESSION,
        sessionRef,
        code.name()), operatorUserId, null);
  }

  private void recordAudit(AdminOperationAuditEvent event, long operatorUserId, Long targetUserId) {
    try {
      auditRecorder.record(event);
    } catch (RuntimeException exception) {
      log.error(
          "Failed to record auth session administration audit. operatorUserId={} targetUserId={} outcome={}",
          operatorUserId,
          targetUserId,
          event.outcome(),
          exception);
    }
  }
}
