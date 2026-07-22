package org.congcong.algomentor.cache.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.stream.Collectors;
import org.congcong.algomentor.cache.caffeine.CaffeineLocalCacheRegionFactory;
import org.congcong.algomentor.cache.api.LocalBoundedCacheRegion;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.LocalBoundedCacheSpec;
import org.junit.jupiter.api.Test;

class MicrometerCacheMetricsTest {

  @Test
  void recordsOnlyFixedCacheAndOutcomeTags() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    CaffeineLocalCacheRegionFactory factory = new CaffeineLocalCacheRegionFactory(
        new CacheRegionRegistry(), new MicrometerCacheMetrics(registry));
    LocalBoundedCacheRegion<String, String> region = factory.createBounded(
        new LocalBoundedCacheSpec(new CacheRegionName("metrics-cache"), 4));

    region.get("user-private-key", ignored -> "value");
    region.get("user-private-key", ignored -> "should-not-load");
    region.invalidate("user-private-key");

    assertThat(registry.get(CacheMetricNames.REQUESTS)
        .tag(CacheMetricTags.CACHE, "metrics-cache")
        .tag(CacheMetricTags.RESULT, "miss")
        .counter().count()).isEqualTo(1);
    assertThat(registry.get(CacheMetricNames.REQUESTS)
        .tag(CacheMetricTags.CACHE, "metrics-cache")
        .tag(CacheMetricTags.RESULT, "hit")
        .counter().count()).isEqualTo(1);
    assertThat(registry.get(CacheMetricNames.INVALIDATIONS)
        .tag(CacheMetricTags.CACHE, "metrics-cache")
        .tag(CacheMetricTags.TYPE, "key")
        .tag(CacheMetricTags.RESULT, "success")
        .counter().count()).isEqualTo(1);
    assertThat(registry.get(CacheMetricNames.ESTIMATED_SIZE)
        .tag(CacheMetricTags.CACHE, "metrics-cache")
        .gauge().value()).isZero();

    Set<String> tags = registry.getMeters().stream()
        .flatMap(meter -> meter.getId().getTags().stream())
        .map(Tag::getKey)
        .collect(Collectors.toSet());
    assertThat(tags).containsOnly(CacheMetricTags.CACHE, CacheMetricTags.RESULT, CacheMetricTags.TYPE,
        CacheMetricTags.CAUSE);
  }
}
