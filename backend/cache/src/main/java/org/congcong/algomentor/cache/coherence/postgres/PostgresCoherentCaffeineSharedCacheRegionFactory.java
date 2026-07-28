package org.congcong.algomentor.cache.coherence.postgres;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.caffeine.CaffeineSharedTtlCacheRegion;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.metrics.CacheCoherenceMetrics;
import org.congcong.algomentor.cache.metrics.CacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionDefinition;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.registry.SharedCacheInvalidationTargetRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** PostgreSQL events coordinate precise invalidation of node-local Caffeine values. */
public final class PostgresCoherentCaffeineSharedCacheRegionFactory
    implements SharedCacheRegionFactory {

  private static final Logger log = LoggerFactory.getLogger(
      PostgresCoherentCaffeineSharedCacheRegionFactory.class);

  private final CacheRegionRegistry registry;
  private final SharedCacheInvalidationTargetRegistry invalidationTargets;
  private final CacheMetrics metrics;
  private final CacheCoherenceMetrics coherenceMetrics;
  private final Duration eventRetention;
  private final Ticker ticker;
  private final Map<CacheRegionName, SharedTtlCacheRegion<?, ?>> regions = new ConcurrentHashMap<>();
  private final Map<CacheRegionName, SharedCacheKeyCodec<?>> keyCodecs = new ConcurrentHashMap<>();

  public PostgresCoherentCaffeineSharedCacheRegionFactory(
      CacheRegionRegistry registry,
      SharedCacheInvalidationTargetRegistry invalidationTargets,
      CacheMetrics metrics,
      CacheCoherenceMetrics coherenceMetrics) {
    this(registry, invalidationTargets, metrics, coherenceMetrics, Ticker.systemTicker());
  }

  public PostgresCoherentCaffeineSharedCacheRegionFactory(
      CacheRegionRegistry registry,
      SharedCacheInvalidationTargetRegistry invalidationTargets,
      CacheMetrics metrics,
      CacheCoherenceMetrics coherenceMetrics,
      Duration eventRetention) {
    this(registry, invalidationTargets, metrics, coherenceMetrics, Ticker.systemTicker(), eventRetention);
  }

  PostgresCoherentCaffeineSharedCacheRegionFactory(
      CacheRegionRegistry registry,
      SharedCacheInvalidationTargetRegistry invalidationTargets,
      CacheMetrics metrics,
      CacheCoherenceMetrics coherenceMetrics,
      Ticker ticker) {
    this(registry, invalidationTargets, metrics, coherenceMetrics, ticker, null);
  }

  private PostgresCoherentCaffeineSharedCacheRegionFactory(
      CacheRegionRegistry registry,
      SharedCacheInvalidationTargetRegistry invalidationTargets,
      CacheMetrics metrics,
      CacheCoherenceMetrics coherenceMetrics,
      Ticker ticker,
      Duration eventRetention) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
    this.invalidationTargets = Objects.requireNonNull(
        invalidationTargets, "invalidationTargets must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.coherenceMetrics = Objects.requireNonNull(coherenceMetrics, "coherenceMetrics must not be null");
    this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
    this.eventRetention = eventRetention;
    log.info("Cache shared provider initialized: provider=postgres-coherent-caffeine scope=cluster");
  }

  @Override
  public <K, V> SharedTtlCacheRegion<K, V> createTtl(
      SharedTtlCacheSpec specification,
      SharedCacheKeyCodec<K> keyCodec) {
    Objects.requireNonNull(specification, "specification must not be null");
    Objects.requireNonNull(keyCodec, "keyCodec must not be null");
    if (eventRetention != null && eventRetention.compareTo(specification.ttl()) <= 0) {
      throw new IllegalArgumentException(
          "eventRetention must be greater than shared cache TTL for " + specification.name().value());
    }
    registry.register(CacheRegionDefinition.sharedTtl(specification));
    SharedCacheKeyCodec<?> existingCodec = keyCodecs.putIfAbsent(specification.name(), keyCodec);
    if (existingCodec != null && existingCodec != keyCodec) {
      throw new IllegalStateException("Shared cache '" + specification.name().value()
          + "' was already created with a different key codec instance");
    }
    SharedTtlCacheRegion<?, ?> region = regions.computeIfAbsent(specification.name(), ignored -> {
      CaffeineSharedTtlCacheRegion<K, V> created = new CaffeineSharedTtlCacheRegion<>(
          specification, keyCodec, ticker, metrics, coherenceMetrics);
      invalidationTargets.register(created);
      return created;
    });
    CaffeineSharedTtlCacheRegion<?, ?> invalidationTarget = (CaffeineSharedTtlCacheRegion<?, ?>) region;
    registry.registerSharedRegion(
        specification.name(), invalidationTarget, invalidationTarget::invalidateAllLocal);
    return cast(region);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> SharedTtlCacheRegion<K, V> cast(SharedTtlCacheRegion<?, ?> region) {
    return (SharedTtlCacheRegion<K, V>) region;
  }
}
