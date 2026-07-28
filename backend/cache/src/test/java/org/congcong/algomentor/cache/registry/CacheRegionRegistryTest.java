package org.congcong.algomentor.cache.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.cache.api.LocalCacheRegion;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.LocalBoundedCacheSpec;
import org.congcong.algomentor.cache.spec.LocalTtlCacheSpec;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.junit.jupiter.api.Test;

class CacheRegionRegistryTest {

  @Test
  void acceptsRepeatedIdenticalDefinitionsAndRejectsConflictingDefinitions() {
    CacheRegionRegistry registry = new CacheRegionRegistry();
    LocalBoundedCacheSpec first = new LocalBoundedCacheSpec(new CacheRegionName("policy-cache"), 10);

    registry.register(CacheRegionDefinition.bounded(first));
    registry.register(CacheRegionDefinition.bounded(first));

    assertThat(registry.find(first.name())).contains(CacheRegionDefinition.bounded(first));
    assertThatIllegalStateException().isThrownBy(() -> registry.register(CacheRegionDefinition.ttl(
        new LocalTtlCacheSpec(first.name(), 10, Duration.ofSeconds(1)))));
  }

  @Test
  void validatesStableNamesAndTtlSpecs() {
    assertThatIllegalArgumentException().isThrownBy(() -> new CacheRegionName("Policy Cache"));
    assertThatIllegalArgumentException().isThrownBy(() -> new SharedTtlCacheSpec(
        new CacheRegionName("shared-cache"), "shared namespace", 1, 1, Duration.ofSeconds(1)));
    assertThatIllegalArgumentException().isThrownBy(() -> new SharedTtlCacheSpec(
        new CacheRegionName("shared-cache"), "shared-cache", 0, 1, Duration.ofSeconds(1)));
    assertThatIllegalArgumentException().isThrownBy(() -> new LocalBoundedCacheSpec(
        new CacheRegionName("bounded-cache"), 0));
  }

  @Test
  void rejectsDifferentCacheNamesForTheSameSharedNamespaceAndSchemaVersion() {
    CacheRegionRegistry registry = new CacheRegionRegistry();
    SharedTtlCacheSpec first = new SharedTtlCacheSpec(
        new CacheRegionName("first-shared-cache"), "shared-contract", 1, 1, Duration.ofSeconds(1));
    SharedTtlCacheSpec conflicting = new SharedTtlCacheSpec(
        new CacheRegionName("second-shared-cache"), "shared-contract", 1, 1, Duration.ofSeconds(1));

    registry.register(CacheRegionDefinition.sharedTtl(first));

    assertThatIllegalStateException().isThrownBy(() ->
        registry.register(CacheRegionDefinition.sharedTtl(conflicting)));
    assertThat(registry.find(conflicting.name())).isEmpty();
  }

  @Test
  void invalidatesRegisteredLocalAndSharedRegions() {
    CacheRegionRegistry registry = new CacheRegionRegistry();
    AtomicInteger localInvalidations = new AtomicInteger();
    AtomicInteger sharedInvalidations = new AtomicInteger();
    registry.registerLocalRegion(new CacheRegionName("local-cache"), new LocalCacheRegion<String, String>() {
      @Override
      public java.util.Optional<String> getIfPresent(String key) {
        return java.util.Optional.empty();
      }

      @Override
      public String get(String key, java.util.function.Function<? super String, ? extends String> loader) {
        return loader.apply(key);
      }

      @Override
      public void put(String key, String value) {
      }

      @Override
      public void invalidate(String key) {
      }

      @Override
      public void invalidateAll() {
        localInvalidations.incrementAndGet();
      }
    });
    registry.registerSharedRegion(
        new CacheRegionName("shared-cache"), new Object(), sharedInvalidations::incrementAndGet);

    registry.invalidateAllRegions();

    assertThat(localInvalidations).hasValue(1);
    assertThat(sharedInvalidations).hasValue(1);
  }
}
