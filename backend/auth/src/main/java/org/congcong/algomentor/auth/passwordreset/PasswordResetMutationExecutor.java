package org.congcong.algomentor.auth.passwordreset;

import java.time.Instant;
import java.util.function.Supplier;
import org.congcong.algomentor.auth.repository.AuthUserRepository;
import org.congcong.algomentor.auth.session.AuthSessionRevocationService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class PasswordResetMutationExecutor {

  private final AuthUserRepository authUserRepository;
  private final AuthSessionRevocationService sessionRevocationService;
  private final TransactionTemplate transactionTemplate;

  public PasswordResetMutationExecutor(
      AuthUserRepository authUserRepository,
      AuthSessionRevocationService sessionRevocationService,
      PlatformTransactionManager transactionManager
  ) {
    this.authUserRepository = authUserRepository;
    this.sessionRevocationService = sessionRevocationService;
    this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
  }

  public int resetPassword(
      long userId,
      String passwordHash,
      Instant expiresAt,
      long resetBy,
      Instant updatedAt
  ) {
    return execute(() -> {
      if (!authUserRepository.resetPasswordCredential(
          userId,
          passwordHash,
          expiresAt,
          resetBy,
          updatedAt)) {
        throw new PasswordResetException(
            PasswordResetErrorCode.AUTH_PASSWORD_CREDENTIAL_NOT_FOUND,
            "该用户没有密码凭据。");
      }
      if (sessionRevocationService == null) {
        throw new IllegalStateException("Auth session revocation service is unavailable.");
      }
      return sessionRevocationService.revokeSessionsForUser(userId);
    });
  }

  public boolean completePasswordReset(long userId, String passwordHash, Instant changedAt) {
    return execute(() -> authUserRepository.completePasswordReset(userId, passwordHash, changedAt));
  }

  private <T> T execute(Supplier<T> action) {
    if (transactionTemplate == null) {
      return action.get();
    }
    T result = transactionTemplate.execute(status -> action.get());
    if (result == null) {
      throw new IllegalStateException("Password reset transaction returned no result.");
    }
    return result;
  }
}
