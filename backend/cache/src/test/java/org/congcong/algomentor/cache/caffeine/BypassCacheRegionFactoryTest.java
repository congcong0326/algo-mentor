package org.congcong.algomentor.cache.caffeine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.cache.api.LocalTtlCacheRegion;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.LocalTtlCacheSpec;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.junit.jupiter.api.Test;

class BypassCacheRegionFactoryTest {

  @Test
  void bypassModeAlwaysLoadsAndNeverRetainsValues() {
    CacheRegionRegistry registry = new CacheRegionRegistry();
    LocalTtlCacheRegion<String, String> local = new BypassLocalCacheRegionFactory(registry).createTtl(
        new LocalTtlCacheSpec(new CacheRegionName("local-bypass"), 4, Duration.ofSeconds(1)));
    SharedTtlCacheRegion<String, String> shared = new BypassSharedCacheRegionFactory(registry).createTtl(
        new SharedTtlCacheSpec(new CacheRegionName("shared-bypass"), "test-cache", 1, 4,
            Duration.ofSeconds(1)));
    AtomicInteger loads = new AtomicInteger();

    local.put("key", "ignored");
    assertThat(local.getIfPresent("key")).isEmpty();
    assertThat(local.get("key", ignored -> "value-" + loads.incrementAndGet())).isEqualTo("value-1");
    assertThat(local.get("key", ignored -> "value-" + loads.incrementAndGet())).isEqualTo("value-2");
    local.invalidateAll();
    assertThat(shared.get("key", ignored -> "shared")).isEqualTo("shared");
    assertThat(shared.getIfPresent("key")).isEmpty();
  }
}
