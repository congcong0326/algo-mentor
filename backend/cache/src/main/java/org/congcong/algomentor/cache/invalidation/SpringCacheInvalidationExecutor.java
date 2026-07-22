package org.congcong.algomentor.cache.invalidation;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public final class SpringCacheInvalidationExecutor implements CacheInvalidationExecutor {

  private static final Logger log = LoggerFactory.getLogger(SpringCacheInvalidationExecutor.class);

  @Override
  public void afterCommit(Runnable invalidation) {
    Objects.requireNonNull(invalidation, "invalidation must not be null");
    if (TransactionSynchronizationManager.isSynchronizationActive()
        && TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          execute(invalidation);
        }
      });
      return;
    }
    execute(invalidation);
  }

  private void execute(Runnable invalidation) {
    try {
      invalidation.run();
    } catch (RuntimeException exception) {
      log.error("Cache invalidation failed after the write completed", exception);
    }
  }
}
