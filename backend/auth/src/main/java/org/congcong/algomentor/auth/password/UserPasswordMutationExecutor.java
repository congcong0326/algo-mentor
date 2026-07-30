package org.congcong.algomentor.auth.password;

import java.time.Instant;
import java.util.function.Supplier;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.session.AuthSessionRevocationService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 将凭据写入与其他 Session 吊销放入同一应用事务边界。
 * Session 删除是否实际参与数据库事务取决于运行时配置的 Session Repository 和事务管理器；
 * 删除失败会传播异常，使凭据事务在可参与时回滚。
 */
public class UserPasswordMutationExecutor {

  private final AuthUserRepository authUserRepository;
  private final AuthSessionRevocationService sessionRevocationService;
  private final TransactionTemplate transactionTemplate;

  public UserPasswordMutationExecutor(
      AuthUserRepository authUserRepository,
      AuthSessionRevocationService sessionRevocationService,
      PlatformTransactionManager transactionManager
  ) {
    this.authUserRepository = authUserRepository;
    this.sessionRevocationService = sessionRevocationService;
    this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
  }

  public UserPasswordUpdateResult updatePasswordSession(
      long userId,
      String expectedPasswordHash,
      String newPasswordHash,
      String currentSessionId,
      Instant changedAt
  ) {
    return execute(() -> {
      if (!authUserRepository.updatePasswordCredentialCompareAndSet(
          userId,
          expectedPasswordHash,
          newPasswordHash,
          changedAt)) {
        throw new UserPasswordException(
            UserPasswordErrorCode.AUTH_PASSWORD_CHANGED_CONCURRENTLY,
            "密码已被其他请求修改，请重新输入当前密码后再试。");
      }
      return new UserPasswordUpdateResult(
          true,
          UserPasswordUpdateOperation.UPDATED,
          revokeOtherSessions(userId, currentSessionId));
    });
  }

  public UserPasswordUpdateResult updateExternalSession(
      long userId,
      String newPasswordHash,
      String currentSessionId,
      Instant changedAt
  ) {
    return execute(() -> {
      if (authUserRepository.insertPasswordCredentialIfAbsent(userId, newPasswordHash, changedAt)) {
        return new UserPasswordUpdateResult(true, UserPasswordUpdateOperation.CREATED, 0);
      }
      if (!authUserRepository.replacePasswordCredential(userId, newPasswordHash, changedAt)) {
        throw new UserPasswordException(
            UserPasswordErrorCode.AUTH_PASSWORD_UPDATE_FAILED,
            "密码写入失败，请稍后重试。");
      }
      return new UserPasswordUpdateResult(
          true,
          UserPasswordUpdateOperation.UPDATED,
          revokeOtherSessions(userId, currentSessionId));
    });
  }

  private int revokeOtherSessions(long userId, String currentSessionId) {
    if (currentSessionId == null || currentSessionId.isBlank() || sessionRevocationService == null) {
      throw new UserPasswordException(
          UserPasswordErrorCode.AUTH_PASSWORD_UPDATE_FAILED,
          "会话吊销服务暂不可用，请稍后重试。");
    }
    return sessionRevocationService.revokeOtherSessionsForUser(userId, currentSessionId);
  }

  private <T> T execute(Supplier<T> action) {
    if (transactionTemplate == null) {
      return action.get();
    }
    T result = transactionTemplate.execute(status -> action.get());
    if (result == null) {
      throw new UserPasswordException(
          UserPasswordErrorCode.AUTH_PASSWORD_UPDATE_FAILED,
          "密码更新事务未返回结果。");
    }
    return result;
  }
}
