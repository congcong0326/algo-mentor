package org.congcong.algomentor.cache.coherence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.caffeine.CaffeineSharedCacheRegionFactory;
import org.congcong.algomentor.cache.invalidation.SpringCacheInvalidationExecutor;
import org.congcong.algomentor.cache.metrics.CacheCoherenceMetrics;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionalSharedCacheInvalidationCoordinatorTest {

  @AfterEach
  void clearTransactionState() {
    TransactionSynchronizationManager.clear();
  }

  @Test
  void appendsTheEventBeforeCommitAndInvalidatesTheLocalRegionOnlyAfterCommit() {
    RecordingEventStore eventStore = new RecordingEventStore();
    SharedTtlCacheRegion<String, String> region = region();
    region.put("42", "old-value");
    TransactionalSharedCacheInvalidationCoordinator coordinator =
        new TransactionalSharedCacheInvalidationCoordinator(
            eventStore, new SpringCacheInvalidationExecutor(), CacheCoherenceMetrics.noop());
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);

    coordinator.invalidate(region, "42");

    assertThat(eventStore.keyToken).isEqualTo("user-42");
    assertThat(region.getIfPresent("42")).contains("old-value");
    for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCommit();
    }
    assertThat(region.getIfPresent("42")).isEmpty();
  }

  @Test
  void propagatesEventWriteFailuresAndLeavesTheLocalValueUntouched() {
    RecordingEventStore eventStore = new RecordingEventStore();
    eventStore.failure = new IllegalStateException("event table unavailable");
    SharedTtlCacheRegion<String, String> region = region();
    region.put("42", "old-value");
    TransactionalSharedCacheInvalidationCoordinator coordinator =
        new TransactionalSharedCacheInvalidationCoordinator(
            eventStore, new SpringCacheInvalidationExecutor(), CacheCoherenceMetrics.noop());

    assertThatThrownBy(() -> coordinator.invalidate(region, "42"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("event table unavailable");
    assertThat(region.getIfPresent("42")).contains("old-value");
  }

  private static SharedTtlCacheRegion<String, String> region() {
    return new CaffeineSharedCacheRegionFactory(new CacheRegionRegistry(), new NoopCacheMetrics())
        .createTtl(
            new SharedTtlCacheSpec(
                new CacheRegionName("coordinator-test-cache"), "coordinator-test", 1, 10,
                Duration.ofMinutes(1)),
            key -> "user-" + key);
  }

  private static final class RecordingEventStore implements SharedCacheInvalidationEventStore {
    private String keyToken;
    private RuntimeException failure;

    @Override
    public void appendKeyInvalidation(SharedTtlCacheSpec specification, String keyToken) {
      if (failure != null) {
        throw failure;
      }
      this.keyToken = keyToken;
    }

    @Override
    public List<SharedCacheInvalidationEvent> findAfter(long cursor, int batchSize) {
      return List.of();
    }

    @Override
    public long findHighWatermark() {
      return 0;
    }

    @Override
    public OptionalLong findLowestId() {
      return OptionalLong.empty();
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
      return 0;
    }
  }
}
