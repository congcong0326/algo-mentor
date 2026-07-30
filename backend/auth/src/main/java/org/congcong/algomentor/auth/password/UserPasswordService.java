package org.congcong.algomentor.auth.password;

import java.time.Clock;
import java.time.Instant;
import org.congcong.algomentor.auth.model.PasswordCredential;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.security.CurrentAuthenticationContext;
import org.congcong.algomentor.auth.security.CurrentAuthenticationContextResolver;
import org.congcong.algomentor.identity.model.AuthUser;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 当前用户主动设置或修改邮箱登录密码的业务入口。
 */
public class UserPasswordService {

  private static final Logger log = LoggerFactory.getLogger(UserPasswordService.class);

  private final AuthUserRepository authUserRepository;
  private final IdentityUserRepository identityUserRepository;
  private final CurrentAuthenticationContextResolver authenticationContextResolver;
  private final PasswordEncoder passwordEncoder;
  private final UserPasswordMutationExecutor mutationExecutor;
  private final UserPasswordMetrics metrics;
  private final Clock clock;

  public UserPasswordService(
      AuthUserRepository authUserRepository,
      IdentityUserRepository identityUserRepository,
      CurrentAuthenticationContextResolver authenticationContextResolver,
      PasswordEncoder passwordEncoder,
      UserPasswordMutationExecutor mutationExecutor,
      UserPasswordMetrics metrics,
      Clock clock
  ) {
    this.authUserRepository = authUserRepository;
    this.identityUserRepository = identityUserRepository;
    this.authenticationContextResolver = authenticationContextResolver;
    this.passwordEncoder = passwordEncoder;
    this.mutationExecutor = mutationExecutor;
    this.metrics = metrics;
    this.clock = clock;
  }

  public UserPasswordUpdateResult updatePassword(UserPasswordUpdateCommand command) {
    CurrentAuthenticationContext context = authenticationContextResolver.resolve()
        .orElseThrow(() -> new UserPasswordException(
            UserPasswordErrorCode.AUTH_PASSWORD_UPDATE_NOT_ALLOWED,
            "当前登录方式不支持修改密码。"));
    try {
      if (context.principal().passwordChangeRequired()) {
        throw new UserPasswordException(
            UserPasswordErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED,
            "使用其他功能前必须先修改临时密码。");
      }
      validateNewPassword(command);
      UserPasswordUpdateResult result = switch (context.method()) {
        case PASSWORD -> updateForPasswordSession(context, command);
        case OIDC, OAUTH2 -> updateForExternalSession(context, command);
      };
      metrics.recordSuccess(context.method(), result.operation());
      metrics.recordSessionRevocations(result.revokedSessionCount());
      log.info(
          "User password updated. userId={} method={} operation={} revokedSessionCount={}",
          context.principal().userId(),
          context.method(),
          result.operation(),
          result.revokedSessionCount());
      return result;
    } catch (UserPasswordException exception) {
      metrics.recordFailure(context.method(), exception.code());
      log.warn(
          "User password update rejected. userId={} method={} code={}",
          context.principal().userId(),
          context.method(),
          exception.code());
      throw exception;
    } catch (RuntimeException exception) {
      metrics.recordFailure(context.method(), UserPasswordErrorCode.AUTH_PASSWORD_UPDATE_FAILED);
      log.warn(
          "User password update failed. userId={} method={} code={}",
          context.principal().userId(),
          context.method(),
          UserPasswordErrorCode.AUTH_PASSWORD_UPDATE_FAILED,
          exception);
      throw new UserPasswordException(
          UserPasswordErrorCode.AUTH_PASSWORD_UPDATE_FAILED,
          "密码更新失败，请稍后重试。",
          exception);
    }
  }

  private UserPasswordUpdateResult updateForPasswordSession(
      CurrentAuthenticationContext context,
      UserPasswordUpdateCommand command
  ) {
    PasswordCredential credential = authUserRepository.findPasswordCredentialByUserId(context.principal().userId())
        .orElseThrow(() -> new UserPasswordException(
            UserPasswordErrorCode.AUTH_PASSWORD_UPDATE_NOT_ALLOWED,
            "当前密码登录状态缺少密码凭据。"));
    if (command.currentPassword() == null || command.currentPassword().isEmpty()) {
      throw new UserPasswordException(
          UserPasswordErrorCode.AUTH_CURRENT_PASSWORD_REQUIRED,
          "请输入当前密码。");
    }
    if (!passwordEncoder.matches(command.currentPassword(), credential.passwordHash())) {
      throw new UserPasswordException(
          UserPasswordErrorCode.AUTH_CURRENT_PASSWORD_INVALID,
          "当前密码不正确。");
    }
    String newPasswordHash = passwordEncoder.encode(command.newPassword());
    return mutationExecutor.updatePasswordSession(
        context.principal().userId(),
        credential.passwordHash(),
        newPasswordHash,
        command.currentSessionId(),
        Instant.now(clock));
  }

  private UserPasswordUpdateResult updateForExternalSession(
      CurrentAuthenticationContext context,
      UserPasswordUpdateCommand command
  ) {
    long userId = context.principal().userId();
    if (authUserRepository.findPasswordCredentialByUserId(userId).isEmpty()) {
      requirePasswordLoginEmail(userId);
    }
    String newPasswordHash = passwordEncoder.encode(command.newPassword());
    return mutationExecutor.updateExternalSession(
        userId,
        newPasswordHash,
        command.currentSessionId(),
        Instant.now(clock));
  }

  private void requirePasswordLoginEmail(long userId) {
    boolean available = identityUserRepository.findUserById(userId)
        .map(AuthUser::email)
        .filter(email -> !email.isBlank())
        .isPresent();
    if (!available) {
      throw new UserPasswordException(
          UserPasswordErrorCode.AUTH_PASSWORD_LOGIN_EMAIL_UNAVAILABLE,
          "当前账号没有可用于邮箱密码登录的邮箱。");
    }
  }

  private static void validateNewPassword(UserPasswordUpdateCommand command) {
    if (command == null
        || command.newPassword() == null
        || command.newPassword().length() < PasswordPolicyConstraints.MIN_LENGTH
        || !command.newPassword().equals(command.confirmPassword())) {
      throw new UserPasswordException(
          UserPasswordErrorCode.AUTH_PASSWORD_REQUEST_INVALID,
          "两次密码必须一致且至少包含 8 个字符。");
    }
  }
}
