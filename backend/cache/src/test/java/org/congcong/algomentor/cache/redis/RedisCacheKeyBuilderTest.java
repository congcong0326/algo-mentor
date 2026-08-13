package org.congcong.algomentor.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;
import org.junit.jupiter.api.Test;

class RedisCacheKeyBuilderTest {

  @Test
  void buildsVersionedStablePhysicalKey() {
    String key = new RedisCacheKeyBuilder().physicalKey(
        new RedisTtlCacheSpec(new CacheRegionName("access-cache"), "access-cache", 2,
            Duration.ofSeconds(1)),
        "3");

    assertThat(key).isEqualTo("algo-mentor:cache:v2:access-cache:3");
  }

  @Test
  void rejectsUnsafeTokensAndSubMillisecondTtl() {
    RedisTtlCacheSpec specification = new RedisTtlCacheSpec(
        new CacheRegionName("access-cache"), "access-cache", 1, Duration.ofMillis(1));

    assertThatIllegalArgumentException().isThrownBy(() ->
        new RedisCacheKeyBuilder().physicalKey(specification, "contains space"));
    assertThatIllegalArgumentException().isThrownBy(() -> new RedisTtlCacheSpec(
        new CacheRegionName("access-cache"), "access-cache", 1, Duration.ofNanos(1)));
  }
}
