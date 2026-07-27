package org.congcong.algomentor.auth.passwordreset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.auth.password.PasswordPolicyConstraints;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.common.admin.audit.AdminAuditAction;
import org.congcong.algomentor.common.admin.audit.AdminAuditMetadataKey;
import org.congcong.algomentor.common.admin.audit.AdminAuditTargetType;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditEvent;
import org.congcong.algomentor.common.admin.audit.AdminOperationAuditRecorder;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordResetService {

  public static final Duration TEMPORARY_PASSWORD_TTL = Duration.ofHours(24);
  public static final int MIN_PASSWORD_LENGTH = PasswordPolicyConstraints.MIN_LENGTH;

  private final AuthUserRepository authUserRepository;
  private final IdentityUserRepository identityUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final TemporaryPasswordGenerator temporaryPasswordGenerator;
  private final PasswordResetMutationExecutor mutationExecutor;
  private final AdminOperationAuditRecorder auditRecorder;
  private final Clock clock;

  public PasswordResetService(
      AuthUserRepository authUserRepository,
      IdentityUserRepository identityUserRepository,
      PasswordEncoder passwordEncoder,
      TemporaryPasswordGenerator temporaryPasswordGenerator,
      PasswordResetMutationExecutor mutationExecutor,
      AdminOperationAuditRecorder auditRecorder,
      Clock clock
  ) {
    this.authUserRepository = authUserRepository;
    this.identityUserRepository = identityUserRepository;
    this.passwordEncoder = passwordEncoder;
    this.temporaryPasswordGenerator = temporaryPasswordGenerator;
    this.mutationExecutor = mutationExecutor;
    this.auditRecorder = auditRecorder;
    this.clock = clock;
  }

  public PasswordResetResult resetPassword(long userId, long operatorUserId) {
    try {
      if (userId == operatorUserId) {
        throw new PasswordResetException(
            PasswordResetErrorCode.AUTH_PASSWORD_RESET_SELF_FORBIDDEN,
            "管理员不能在后台重置自己的密码。");
      }
      AuthUser user = identityUserRepository.findUserById(userId)
          .filter(found -> found.status() != AuthUserStatus.DELETED)
          .orElseThrow(() -> new PasswordResetException(
              PasswordResetErrorCode.USER_NOT_FOUND,
              "用户不存在。"));
      PasswordCredential credential = authUserRepository.findPasswordCredentialByUserId(user.id())
          .orElseThrow(() -> new PasswordResetException(
              PasswordResetErrorCode.AUTH_PASSWORD_CREDENTIAL_NOT_FOUND,
              "该用户没有密码凭据。"));
      String temporaryPassword = temporaryPasswordGenerator.generate();
      Instant now = Instant.now(clock);
      Instant expiresAt = now.plus(TEMPORARY_PASSWORD_TTL);
      int revokedSessions = mutationExecutor.resetPassword(
          credential.userId(),
          passwordEncoder.encode(temporaryPassword),
          expiresAt,
          operatorUserId,
          now);
      auditRecorder.record(AdminOperationAuditEvent.success(
          operatorUserId,
          AdminAuditAction.USER_PASSWORD_RESET,
          AdminAuditTargetType.USER,
          Long.toString(userId),
          Map.of(AdminAuditMetadataKey.REVOKED_SESSION_COUNT, revokedSessions)));
      return new PasswordResetResult(temporaryPassword, expiresAt, revokedSessions);
    } catch (PasswordResetException exception) {
      recordFailure(operatorUserId, userId, exception.code());
      throw exception;
    } catch (RuntimeException exception) {
      recordFailure(operatorUserId, userId, PasswordResetErrorCode.AUTH_PASSWORD_RESET_FAILED);
      throw new PasswordResetException(
          PasswordResetErrorCode.AUTH_PASSWORD_RESET_FAILED,
          "密码重置失败，请稍后重试。",
          exception);
    }
  }

  public AuthenticatedUserPrincipal completeReset(
      AuthenticatedUserPrincipal principal,
      String newPassword,
      String confirmPassword
  ) {
    if (principal == null || !principal.passwordChangeRequired()) {
      throw new PasswordResetException(
          PasswordResetErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED,
          "当前 Session 不需要完成临时密码修改。");
    }
    if (newPassword == null
        || !newPassword.equals(confirmPassword)
        || newPassword.length() < MIN_PASSWORD_LENGTH) {
      throw new PasswordResetException(
          PasswordResetErrorCode.AUTH_REQUEST_INVALID,
          "两次密码必须一致且至少包含 8 个字符。");
    }
    Instant now = Instant.now(clock);
    if (!mutationExecutor.completePasswordReset(
        principal.userId(),
        passwordEncoder.encode(newPassword),
        now)) {
      throw new PasswordResetException(
          PasswordResetErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED,
          "临时密码状态已失效，请重新登录或联系管理员。");
    }
    return principal.withPasswordChangeRequired(false);
  }

  private void recordFailure(long operatorUserId, long userId, PasswordResetErrorCode code) {
    auditRecorder.record(AdminOperationAuditEvent.failure(
        operatorUserId,
        AdminAuditAction.USER_PASSWORD_RESET,
        AdminAuditTargetType.USER,
        userId < 1 ? null : Long.toString(userId),
        code.name()));
  }
}
