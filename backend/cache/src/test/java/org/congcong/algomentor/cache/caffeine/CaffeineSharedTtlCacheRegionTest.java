package org.congcong.algomentor.cache.caffeine;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.cache.metrics.CacheCoherenceMetrics;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.junit.jupiter.api.Test;

class CaffeineSharedTtlCacheRegionTest {

  @Test
  void usesTheKeyCodecForGetPutAndInvalidation() {
    AtomicInteger codecCalls = new AtomicInteger();
    CaffeineSharedTtlCacheRegion<String, String> region = region(key -> {
      codecCalls.incrementAndGet();
      return "digest-" + key;
    });

    region.put("42", "first");
    assertThat(region.getIfPresent("42")).contains("first");
    region.invalidate("42");
    assertThat(region.getIfPresent("42")).isEmpty();
    assertThat(codecCalls).hasValue(4);
  }

  @Test
  void discardsAnOldLoaderResultWhenInvalidatedDuringTheLoad() throws Exception {
    CaffeineSharedTtlCacheRegion<String, String> region = region(key -> "user-" + key);
    CountDownLatch loaderStarted = new CountDownLatch(1);
    CountDownLatch allowOldLoaderToFinish = new CountDownLatch(1);
    AtomicInteger loads = new AtomicInteger();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<String> read = executor.submit(() -> region.get("42", ignored -> {
        if (loads.incrementAndGet() == 1) {
          loaderStarted.countDown();
          await(allowOldLoaderToFinish);
          return "old";
        }
        return "new";
      }));
      assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).isTrue();

      Future<?> invalidation = executor.submit(() -> region.invalidate("42"));
      awaitGeneration(region, "user-42");
      allowOldLoaderToFinish.countDown();

      assertThat(read.get(5, TimeUnit.SECONDS)).isEqualTo("new");
      invalidation.get(5, TimeUnit.SECONDS);
      assertThat(region.getIfPresent("42").orElse(null)).isNotEqualTo("old");
      assertThat(loads).hasValue(2);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void removesGenerationStateAfterExactInvalidationWithoutAnInFlightLoad() {
    CaffeineSharedTtlCacheRegion<String, String> region = region(key -> "user-" + key);

    region.put("42", "value");
    region.invalidate("42");
    region.cleanUp();

    assertThat(region.generationCount()).isZero();
  }

  private static CaffeineSharedTtlCacheRegion<String, String> region(
      org.congcong.algomentor.cache.spec.SharedCacheKeyCodec<String> codec) {
    return new CaffeineSharedTtlCacheRegion<>(
        new SharedTtlCacheSpec(
            new CacheRegionName("shared-test-cache"), "shared-test", 1, 32, Duration.ofMinutes(1)),
        codec,
        Ticker.systemTicker(),
        new NoopCacheMetrics(),
        CacheCoherenceMetrics.noop());
  }

  private static void awaitGeneration(CaffeineSharedTtlCacheRegion<?, ?> region, String keyToken)
      throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (region.generation(keyToken) == 0 && System.nanoTime() < deadline) {
      Thread.sleep(1);
    }
    assertThat(region.generation(keyToken)).isPositive();
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
