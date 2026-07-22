package org.congcong.algomentor.auth.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.cache.caffeine.CaffeineSharedCacheRegionFactory;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationEvent;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationEventStore;
import org.congcong.algomentor.cache.coherence.TransactionalSharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor;
import org.congcong.algomentor.cache.metrics.CacheCoherenceMetrics;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.junit.jupiter.api.Test;

class BetaAccessCacheTest {

  @Test
  void cachesMembershipAndPublishesOnlyHashedEmailTokens() {
    RecordingEventStore eventStore = new RecordingEventStore();
    BetaAccessCache cache = new BetaAccessCache(
        new CaffeineSharedCacheRegionFactory(new CacheRegionRegistry(), new NoopCacheMetrics()),
        new TransactionalSharedCacheInvalidationCoordinator(
            eventStore, Runnable::run, CacheCoherenceMetrics.noop()),
        new AuthCacheProperties());
    String email = "user@example.com";
    AtomicInteger loads = new AtomicInteger();

    assertThat(cache.isAllowedEmail(email, () -> loads.incrementAndGet() == 1)).isTrue();
    assertThat(cache.isAllowedEmail(email, () -> false)).isTrue();
    assertThat(loads).hasValue(1);

    cache.invalidateEmail(email);

    assertThat(eventStore.keyToken).isNotEqualTo(email).hasSize(64).matches("[0-9a-f]+");
    assertThat(cache.isAllowedEmail(email, () -> {
      loads.incrementAndGet();
      return false;
    })).isFalse();
    assertThat(loads).hasValue(2);
  }

  private static final class RecordingEventStore implements SharedCacheInvalidationEventStore {
    private String keyToken;

    @Override
    public void appendKeyInvalidation(SharedTtlCacheSpec specification, String keyToken) {
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
