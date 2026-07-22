package org.congcong.algomentor.cache.caffeine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.congcong.algomentor.cache.api.LocalBoundedCacheRegion;
import org.congcong.algomentor.cache.api.LocalTtlCacheRegion;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.LocalBoundedCacheSpec;
import org.congcong.algomentor.cache.spec.LocalTtlCacheSpec;
import org.junit.jupiter.api.Test;

class CaffeineCacheRegionTest {

  @Test
  void evictsEntriesWhenBoundedRegionExceedsMaximumSize() {
    CaffeineCacheRegion<String, String> region = region(1, null, Ticker.systemTicker());

    region.put("first", "one");
    region.put("second", "two");
    region.cleanUp();

    assertThat(region.estimatedSize()).isLessThanOrEqualTo(1);
  }

  @Test
  void expiresTtlEntriesAfterWriteUsingTicker() {
    AtomicLong nanos = new AtomicLong();
    Ticker ticker = nanos::get;
    CaffeineCacheRegion<String, String> region = region(4, Duration.ofSeconds(5), ticker);

    region.put("key", "value");
    nanos.addAndGet(Duration.ofSeconds(5).plusNanos(1).toNanos());
    region.cleanUp();

    assertThat(region.getIfPresent("key")).isEmpty();
  }

  @Test
  void loadsSameMissingKeyOnlyOnceConcurrently() throws Exception {
    CaffeineCacheRegion<String, String> region = region(10, null, Ticker.systemTicker());
    AtomicInteger loads = new AtomicInteger();
    CountDownLatch loaderStarted = new CountDownLatch(1);
    CountDownLatch allowLoaderToFinish = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(12);
    try {
      List<Future<String>> results = new ArrayList<>();
      for (int index = 0; index < 12; index++) {
        results.add(executor.submit(() -> region.get("key", ignored -> {
          loads.incrementAndGet();
          loaderStarted.countDown();
          await(allowLoaderToFinish);
          return "value";
        })));
      }

      assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();
      allowLoaderToFinish.countDown();

      for (Future<String> result : results) {
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("value");
      }
      assertThat(loads).hasValue(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void doesNotCacheLoaderExceptionsOrNullResults() {
    CaffeineCacheRegion<String, String> region = region(10, null, Ticker.systemTicker());

    assertThatThrownBy(() -> region.get("key", ignored -> {
      throw new IllegalStateException("repository failure");
    })).isInstanceOf(IllegalStateException.class).hasMessage("repository failure");
    assertThatThrownBy(() -> region.get("key", ignored -> null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("loader result must not be null");

    assertThat(region.get("key", ignored -> "reloaded")).isEqualTo("reloaded");
  }

  @Test
  void rejectsNullInputsAndSupportsExactAndLocalFullInvalidation() {
    CaffeineLocalCacheRegionFactory factory = new CaffeineLocalCacheRegionFactory(
        new CacheRegionRegistry(), new NoopCacheMetrics());
    LocalBoundedCacheRegion<String, String> bounded = factory.createBounded(
        new LocalBoundedCacheSpec(new CacheRegionName("bounded-cache"), 4));
    LocalTtlCacheRegion<String, String> ttl = factory.createTtl(
        new LocalTtlCacheSpec(new CacheRegionName("ttl-cache"), 4, Duration.ofMinutes(1)));

    assertThatNullPointerException().isThrownBy(() -> bounded.getIfPresent(null));
    assertThatNullPointerException().isThrownBy(() -> bounded.put("key", null));
    assertThatNullPointerException().isThrownBy(() -> bounded.get("key", null));

    bounded.put("first", "one");
    bounded.put("second", "two");
    bounded.invalidate("first");
    assertThat(bounded.getIfPresent("first")).isEmpty();
    assertThat(bounded.getIfPresent("second")).contains("two");
    bounded.invalidateAll();
    assertThat(bounded.getIfPresent("second")).isEmpty();

    ttl.put("key", "value");
    ttl.invalidateAll();
    assertThat(ttl.getIfPresent("key")).isEmpty();
    assertThatIllegalArgumentException().isThrownBy(() -> new LocalTtlCacheSpec(
        new CacheRegionName("invalid-ttl"), 1, Duration.ZERO));
  }

  private CaffeineCacheRegion<String, String> region(long maximumSize, Duration ttl, Ticker ticker) {
    return CaffeineCacheRegion.create(
        new CacheRegionName("test-cache"), maximumSize, ttl, ticker, new NoopCacheMetrics());
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
