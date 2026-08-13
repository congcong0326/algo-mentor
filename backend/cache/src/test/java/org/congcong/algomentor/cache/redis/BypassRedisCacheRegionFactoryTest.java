package org.congcong.algomentor.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.cache.api.RedisTtlCacheRegion;
import org.congcong.algomentor.cache.redis.codec.JacksonRedisValueCodecFactory;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;
import org.junit.jupiter.api.Test;

class BypassRedisCacheRegionFactoryTest {

  @Test
  void bypassesValueStorageAndRejectsConflictingValueCodec() {
    BypassRedisCacheRegionFactory factory = new BypassRedisCacheRegionFactory(new CacheRegionRegistry());
    RedisTtlCacheSpec spec = new RedisTtlCacheSpec(
        new CacheRegionName("redis-test"), "redis-test", 1, Duration.ofSeconds(1));
    JacksonRedisValueCodecFactory codecs = JacksonRedisValueCodecFactory.standalone();
    RedisTtlCacheRegion<String, String> region = factory.createTtl(spec, key -> key, codecs.json(String.class));
    AtomicInteger loads = new AtomicInteger();

    assertThat(region.get("key", ignored -> "value-" + loads.incrementAndGet())).isEqualTo("value-1");
    assertThat(region.get("key", ignored -> "value-" + loads.incrementAndGet())).isEqualTo("value-2");
    assertThat(region.getIfPresent("key")).isEmpty();
    assertThatIllegalStateException().isThrownBy(() ->
        factory.<String, String>createTtl(spec, key -> key, codecs.json(String.class)));
  }
}
