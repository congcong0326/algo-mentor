package org.congcong.algomentor.auth.betaaccess.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.congcong.algomentor.auth.betaaccess.model.BetaAccessSettings;
import org.congcong.algomentor.auth.betaaccess.model.BetaAccessOverviewSummary;
import org.congcong.algomentor.auth.betaaccess.model.BetaAccessUserMembership;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmail;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmailAddResult;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmailAddStatus;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmailBatchResult;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmailPage;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmailRemovalResult;
import org.congcong.algomentor.auth.betaaccess.repository.BetaAccessRepository;
import org.congcong.algomentor.auth.session.AuthSessionRevocationService;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminAuditTargetType;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetaAccessAdminService {

  public static final int MAX_BATCH_SIZE = 100;
  public static final int MAX_PAGE_SIZE = 100;
  private static final Logger log = LoggerFactory.getLogger(BetaAccessAdminService.class);

  private final BetaAccessRepository repository;
  private final IdentityUserRepository identityUserRepository;
  private final AuthSessionRevocationService sessionRevocationService;
  private final AdminOperationAuditRecorder auditRecorder;
  private final BetaAllowedEmailRemovalExecutor removalExecutor;
  private final BetaAccessMetrics metrics;
  private final Clock clock;

  public BetaAccessAdminService(
      BetaAccessRepository repository,
      IdentityUserRepository identityUserRepository,
      AuthSessionRevocationService sessionRevocationService,
      AdminOperationAuditRecorder auditRecorder,
      BetaAllowedEmailRemovalExecutor removalExecutor,
      BetaAccessMetrics metrics,
      Clock clock
  ) {
    this.repository = repository;
    this.identityUserRepository = identityUserRepository;
    this.sessionRevocationService = sessionRevocationService;
    this.auditRecorder = auditRecorder;
    this.removalExecutor = removalExecutor;
    this.metrics = metrics;
    this.clock = clock;
  }

  public BetaAllowedEmailPage getPage(int page, int pageSize, String keyword) {
    if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
      throw new BetaAccessException(BetaAccessErrorCode.AUTH_REQUEST_INVALID, "分页参数不合法。");
    }
    String normalizedKeyword = keyword == null ? "" : keyword.trim();
    BetaAccessSettings settings = requireSettings();
    return new BetaAllowedEmailPage(
        settings,
        repository.findAllowedEmails(normalizedKeyword, pageSize, (page - 1) * pageSize),
        repository.countAllowedEmails(normalizedKeyword),
        page,
        pageSize);
  }

  public BetaAccessOverviewSummary overviewSummary() {
    return repository.overviewSummary();
  }

  public BetaAccessUserMembership userMembership(long userId) {
    if (userId < 1 || identityUserRepository.findUserById(userId).isEmpty()) {
      throw new BetaAccessException(BetaAccessErrorCode.AUTH_REQUEST_INVALID, "用户不存在。");
    }
    return repository.userMembership(userId);
  }

  public BetaAccessSettings updateSettings(Boolean emailAllowlistEnabled, long operatorUserId) {
    if (emailAllowlistEnabled == null) {
      recordFailure(
          operatorUserId,
          AdminAuditAction.BETA_ACCESS_SETTING_UPDATE,
          AdminAuditTargetType.BETA_ACCESS_SETTINGS,
          "1",
          BetaAccessErrorCode.AUTH_REQUEST_INVALID);
      throw new BetaAccessException(BetaAccessErrorCode.AUTH_REQUEST_INVALID, "白名单开关不能为空。");
    }
    BetaAccessSettings current = requireSettings();
    if (current.emailAllowlistEnabled() == emailAllowlistEnabled) {
      return current;
    }
    Instant now = Instant.now(clock);
    if (!repository.updateSettings(emailAllowlistEnabled, operatorUserId, now)) {
      recordFailure(
          operatorUserId,
          AdminAuditAction.BETA_ACCESS_SETTING_UPDATE,
          AdminAuditTargetType.BETA_ACCESS_SETTINGS,
          "1",
          BetaAccessErrorCode.BETA_ACCESS_SETTINGS_CONFLICT);
      throw new BetaAccessException(
          BetaAccessErrorCode.BETA_ACCESS_SETTINGS_CONFLICT,
          "内测准入设置更新冲突。");
    }
    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        AdminAuditAction.BETA_ACCESS_SETTING_UPDATE,
        AdminAuditTargetType.BETA_ACCESS_SETTINGS,
        "1",
        Map.of(AdminAuditMetadataKey.EMAIL_ALLOWLIST_ENABLED, emailAllowlistEnabled)));
    return requireSettings();
  }

  public BetaAllowedEmailBatchResult addEmails(List<String> emails, long operatorUserId) {
    if (emails == null || emails.isEmpty() || emails.size() > MAX_BATCH_SIZE) {
      recordFailure(
          operatorUserId,
          AdminAuditAction.BETA_ALLOWED_EMAIL_ADD,
          AdminAuditTargetType.BETA_ALLOWED_EMAIL,
          null,
          BetaAccessErrorCode.AUTH_REQUEST_INVALID);
      throw new BetaAccessException(
          BetaAccessErrorCode.AUTH_REQUEST_INVALID,
          "每次需要提交 1 到 100 个邮箱。");
    }
    Set<String> seen = new HashSet<>();
    List<BetaAllowedEmailAddResult> results = new ArrayList<>(emails.size());
    int added = 0;
    int existing = 0;
    int invalid = 0;
    for (String email : emails) {
      if (!BetaEmailAddress.isValid(email)) {
        results.add(new BetaAllowedEmailAddResult(email, BetaAllowedEmailAddStatus.INVALID, null));
        invalid++;
        recordFailure(
            operatorUserId,
            AdminAuditAction.BETA_ALLOWED_EMAIL_ADD,
            AdminAuditTargetType.BETA_ALLOWED_EMAIL,
            null,
            BetaAccessErrorCode.BETA_ACCESS_EMAIL_INVALID);
        continue;
      }
      String normalized = BetaEmailAddress.normalize(email);
      if (!seen.add(normalized)) {
        results.add(new BetaAllowedEmailAddResult(email, BetaAllowedEmailAddStatus.EXISTING, null));
        existing++;
        continue;
      }
      Optional<BetaAllowedEmail> existingRecord = repository.findAllowedEmailByNormalized(normalized);
      if (existingRecord.isPresent()) {
        results.add(new BetaAllowedEmailAddResult(
            email,
            BetaAllowedEmailAddStatus.EXISTING,
            existingRecord.get().id()));
        existing++;
        continue;
      }
      Optional<Long> insertedId = repository.insertAllowedEmail(
          email.trim(),
          normalized,
          operatorUserId,
          Instant.now(clock));
      if (insertedId.isEmpty()) {
        Optional<BetaAllowedEmail> racedRecord = repository.findAllowedEmailByNormalized(normalized);
        results.add(new BetaAllowedEmailAddResult(
            email,
            BetaAllowedEmailAddStatus.EXISTING,
            racedRecord.map(BetaAllowedEmail::id).orElse(null)));
        existing++;
        continue;
      }
      long allowedEmailId = insertedId.get();
      results.add(new BetaAllowedEmailAddResult(email, BetaAllowedEmailAddStatus.ADDED, allowedEmailId));
      added++;
      auditRecorder.record(AdminOperationAuditEvent.success(
          operatorUserId,
          AdminAuditAction.BETA_ALLOWED_EMAIL_ADD,
          AdminAuditTargetType.BETA_ALLOWED_EMAIL,
          Long.toString(allowedEmailId),
          Map.of()));
    }
    return new BetaAllowedEmailBatchResult(added, existing, invalid, results);
  }

  public BetaAllowedEmailRemovalResult removeEmail(long allowedEmailId, long operatorUserId) {
    if (allowedEmailId < 1) {
      recordFailure(
          operatorUserId,
          AdminAuditAction.BETA_ALLOWED_EMAIL_REMOVE,
          AdminAuditTargetType.BETA_ALLOWED_EMAIL,
          null,
          BetaAccessErrorCode.AUTH_REQUEST_INVALID);
      throw new BetaAccessException(BetaAccessErrorCode.AUTH_REQUEST_INVALID, "白名单记录 ID 不合法。");
    }
    BetaAllowedEmail removed = removalExecutor.remove(allowedEmailId)
        .orElseThrow(() -> {
          recordFailure(
              operatorUserId,
              AdminAuditAction.BETA_ALLOWED_EMAIL_REMOVE,
              AdminAuditTargetType.BETA_ALLOWED_EMAIL,
              Long.toString(allowedEmailId),
              BetaAccessErrorCode.BETA_ACCESS_EMAIL_NOT_FOUND);
          return new BetaAccessException(
              BetaAccessErrorCode.BETA_ACCESS_EMAIL_NOT_FOUND,
              "白名单记录不存在。");
        });

    Optional<AuthUser> associatedUser = identityUserRepository.findUserByEmailNormalized(
        removed.emailNormalized());
    int revokedSessions = 0;
    boolean revocationSucceeded = true;
    if (associatedUser.isPresent()) {
      if (sessionRevocationService == null) {
        revocationSucceeded = false;
        metrics.recordSessionRevocationFailure();
      } else {
        try {
          revokedSessions = sessionRevocationService.revokeSessionsForUser(associatedUser.get().id());
        } catch (RuntimeException exception) {
          revocationSucceeded = false;
          metrics.recordSessionRevocationFailure();
          log.warn(
              "Failed to revoke sessions after beta allowlist removal. allowedEmailId={} userId={}",
              allowedEmailId,
              associatedUser.get().id(),
              exception);
        }
      }
    }

    auditRecorder.record(AdminOperationAuditEvent.success(
        operatorUserId,
        AdminAuditAction.BETA_ALLOWED_EMAIL_REMOVE,
        AdminAuditTargetType.BETA_ALLOWED_EMAIL,
        Long.toString(allowedEmailId),
        Map.of(
            AdminAuditMetadataKey.REVOKED_SESSION_COUNT, revokedSessions,
            AdminAuditMetadataKey.SESSION_REVOCATION_SUCCEEDED, revocationSucceeded)));
    AuthUser user = associatedUser.orElse(null);
    return new BetaAllowedEmailRemovalResult(
        allowedEmailId,
        user == null ? null : user.id(),
        user == null ? null : user.status(),
        revokedSessions,
        revocationSucceeded);
  }

  private BetaAccessSettings requireSettings() {
    return repository.findSettings().orElseThrow(() -> new BetaAccessException(
        BetaAccessErrorCode.BETA_ACCESS_SETTINGS_CONFLICT,
        "内测准入设置不存在。"));
  }

  private void recordFailure(
      long operatorUserId,
      AdminAuditAction action,
      AdminAuditTargetType targetType,
      String targetRef,
      BetaAccessErrorCode errorCode
  ) {
    auditRecorder.record(AdminOperationAuditEvent.failure(
        operatorUserId,
        action,
        targetType,
        targetRef,
        errorCode.name()));
  }
}
