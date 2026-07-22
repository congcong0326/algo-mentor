package org.congcong.algomentor.cache.invalidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SpringCacheInvalidationExecutorTest {

  private final SpringCacheInvalidationExecutor executor = new SpringCacheInvalidationExecutor();

  @AfterEach
  void clearTransactionState() {
    TransactionSynchronizationManager.clear();
  }

  @Test
  void executesImmediatelyOutsideTransactionAndSuppressesInvalidationFailure() {
    AtomicInteger invalidations = new AtomicInteger();

    executor.afterCommit(invalidations::incrementAndGet);

    assertThat(invalidations).hasValue(1);
    assertThatCode(() -> executor.afterCommit(() -> {
      throw new IllegalStateException("cache backend failed");
    })).doesNotThrowAnyException();
  }

  @Test
  void executesOnlyAfterTransactionCommit() {
    AtomicInteger invalidations = new AtomicInteger();
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);

    executor.afterCommit(invalidations::incrementAndGet);

    assertThat(invalidations).hasValue(0);
    TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    assertThat(invalidations).hasValue(1);
  }

  @Test
  void doesNotExecuteWhenTransactionRollsBack() {
    AtomicInteger invalidations = new AtomicInteger();
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);

    executor.afterCommit(invalidations::incrementAndGet);
    TransactionSynchronizationManager.getSynchronizations().forEach(
        synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    assertThat(invalidations).hasValue(0);
  }
}
