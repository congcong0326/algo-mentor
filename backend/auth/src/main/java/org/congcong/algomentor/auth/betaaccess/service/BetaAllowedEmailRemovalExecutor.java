package org.congcong.algomentor.auth.betaaccess.service;

import java.util.Optional;
import org.congcong.algomentor.auth.cache.BetaAccessCache;
import org.congcong.algomentor.auth.betaaccess.model.BetaAllowedEmail;
import org.congcong.algomentor.auth.betaaccess.repository.BetaAccessRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class BetaAllowedEmailRemovalExecutor {

  private final BetaAccessRepository repository;
  private final TransactionTemplate transactionTemplate;
  private final BetaAccessCache cache;

  public BetaAllowedEmailRemovalExecutor(
      BetaAccessRepository repository,
      PlatformTransactionManager transactionManager
  ) {
    this(repository, transactionManager, null);
  }

  public BetaAllowedEmailRemovalExecutor(
      BetaAccessRepository repository,
      PlatformTransactionManager transactionManager,
      BetaAccessCache cache
  ) {
    this.repository = repository;
    this.cache = cache;
    if (transactionManager == null) {
      this.transactionTemplate = null;
    } else {
      this.transactionTemplate = new TransactionTemplate(transactionManager);
      this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
  }

  public Optional<BetaAllowedEmail> remove(long allowedEmailId) {
    if (transactionTemplate == null) {
      return removeWithinTransaction(allowedEmailId);
    }
    Optional<BetaAllowedEmail> result = transactionTemplate.execute(
        status -> removeWithinTransaction(allowedEmailId));
    return result == null ? Optional.empty() : result;
  }

  private Optional<BetaAllowedEmail> removeWithinTransaction(long allowedEmailId) {
    Optional<BetaAllowedEmail> existing = repository.findAllowedEmailById(allowedEmailId);
    if (existing.isEmpty() || !repository.deleteAllowedEmail(allowedEmailId)) {
      return Optional.empty();
    }
    if (cache != null) {
      cache.invalidateEmail(existing.get().emailNormalized());
    }
    return existing;
  }
}
