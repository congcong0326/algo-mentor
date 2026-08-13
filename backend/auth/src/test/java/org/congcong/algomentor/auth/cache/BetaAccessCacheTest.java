package org.congcong.algomentor.auth.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.cache.redis.BypassRedisCacheRegionFactory;
import org.congcong.algomentor.cache.redis.codec.JacksonRedisValueCodecFactory;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.junit.jupiter.api.Test;

class BetaAccessCacheTest {

  @Test
  void bypassFactoryPreservesBusinessResultsAndNeverExposesEmailAsAnEventToken() {
    BetaAccessCache cache = new BetaAccessCache(
        new BypassRedisCacheRegionFactory(new CacheRegionRegistry()),
        JacksonRedisValueCodecFactory.standalone(),
        Runnable::run,
        new AuthCacheProperties());
    String email = "user@example.com";
    AtomicInteger loads = new AtomicInteger();

    assertThat(cache.isAllowedEmail(email, () -> loads.incrementAndGet() == 1)).isTrue();
    assertThat(cache.isAllowedEmail(email, () -> false)).isFalse();
    assertThat(loads).hasValue(1);

    cache.invalidateEmail(email);

    assertThat(cache.isAllowedEmail(email, () -> {
      loads.incrementAndGet();
      return false;
    })).isFalse();
    assertThat(loads).hasValue(2);
  }
}
