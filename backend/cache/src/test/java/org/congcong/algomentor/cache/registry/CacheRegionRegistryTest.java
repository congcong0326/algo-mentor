package org.congcong.algomentor.cache.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
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
}
