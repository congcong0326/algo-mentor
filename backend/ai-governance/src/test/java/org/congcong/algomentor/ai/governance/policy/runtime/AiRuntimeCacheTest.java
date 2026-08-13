package org.congcong.algomentor.ai.governance.policy.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.cache.redis.BypassRedisCacheRegionFactory;
import org.congcong.algomentor.cache.redis.codec.JacksonRedisValueCodecFactory;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.junit.jupiter.api.Test;

class AiRuntimeCacheTest {

  @Test
  void bypassFactoryReturnsMissingSettingsAndAllowsInvalidation() {
    AiRuntimeCache cache = new AiRuntimeCache(
        new BypassRedisCacheRegionFactory(new CacheRegionRegistry()),
        JacksonRedisValueCodecFactory.standalone(),
        Runnable::run,
        new AiRuntimeCacheProperties());
    AtomicInteger loads = new AtomicInteger();

    assertThat(cache.getSettings(() -> {
      loads.incrementAndGet();
      return Optional.empty();
    })).isEmpty();
    assertThat(cache.getSettings(() -> {
      loads.incrementAndGet();
      return Optional.of(new AiRuntimeSettings(true, 50, null, null));
    })).contains(new AiRuntimeSettings(true, 50, null, null));
    assertThat(loads).hasValue(2);

    cache.invalidateSettings();

    assertThat(cache.getSettings(() -> {
      loads.incrementAndGet();
      return Optional.of(new AiRuntimeSettings(false, 20, 1L, java.time.Instant.EPOCH));
    })).contains(new AiRuntimeSettings(false, 20, 1L, java.time.Instant.EPOCH));
    assertThat(loads).hasValue(3);
  }
}
