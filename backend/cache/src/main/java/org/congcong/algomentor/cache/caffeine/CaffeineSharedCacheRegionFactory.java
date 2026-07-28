package org.congcong.algomentor.cache.caffeine;

import com.github.benmanes.caffeine.cache.Ticker;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.metrics.CacheCoherenceMetrics;
import org.congcong.algomentor.cache.metrics.CacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionDefinition;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CaffeineSharedCacheRegionFactory implements SharedCacheRegionFactory {

  private static final Logger log = LoggerFactory.getLogger(CaffeineSharedCacheRegionFactory.class);

  private final CacheRegionRegistry registry;
  private final CacheMetrics metrics;
  private final CacheCoherenceMetrics coherenceMetrics;
  private final Ticker ticker;
  private final Map<CacheRegionName, SharedTtlCacheRegion<?, ?>> regions = new ConcurrentHashMap<>();
  private final Map<CacheRegionName, SharedCacheKeyCodec<?>> keyCodecs = new ConcurrentHashMap<>();

  public CaffeineSharedCacheRegionFactory(CacheRegionRegistry registry, CacheMetrics metrics) {
    this(registry, metrics, CacheCoherenceMetrics.noop(), Ticker.systemTicker());
  }

  public CaffeineSharedCacheRegionFactory(
      CacheRegionRegistry registry,
      CacheMetrics metrics,
      CacheCoherenceMetrics coherenceMetrics) {
    this(registry, metrics, coherenceMetrics, Ticker.systemTicker());
  }

  CaffeineSharedCacheRegionFactory(CacheRegionRegistry registry, CacheMetrics metrics, Ticker ticker) {
    this(registry, metrics, CacheCoherenceMetrics.noop(), ticker);
  }

  CaffeineSharedCacheRegionFactory(
      CacheRegionRegistry registry,
      CacheMetrics metrics,
      CacheCoherenceMetrics coherenceMetrics,
      Ticker ticker) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.coherenceMetrics = Objects.requireNonNull(coherenceMetrics, "coherenceMetrics must not be null");
    this.ticker = Objects.requireNonNull(ticker, "ticker must not be null");
    log.info("Cache shared provider initialized: provider=caffeine scope=single-jvm");
  }

  @Override
  public <K, V> SharedTtlCacheRegion<K, V> createTtl(
      SharedTtlCacheSpec spec,
      SharedCacheKeyCodec<K> keyCodec) {
    Objects.requireNonNull(spec, "spec must not be null");
    Objects.requireNonNull(keyCodec, "keyCodec must not be null");
    registry.register(CacheRegionDefinition.sharedTtl(spec));
    SharedCacheKeyCodec<?> existingCodec = keyCodecs.putIfAbsent(spec.name(), keyCodec);
    if (existingCodec != null && existingCodec != keyCodec) {
      throw new IllegalStateException("Shared cache '" + spec.name().value()
          + "' was already created with a different key codec instance");
    }
    SharedTtlCacheRegion<?, ?> region = regions.computeIfAbsent(spec.name(), ignored ->
        new CaffeineSharedTtlCacheRegion<>(spec, keyCodec, ticker, metrics, coherenceMetrics));
    CaffeineSharedTtlCacheRegion<?, ?> invalidationTarget = (CaffeineSharedTtlCacheRegion<?, ?>) region;
    registry.registerSharedRegion(spec.name(), invalidationTarget, invalidationTarget::invalidateAllLocal);
    return cast(region);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> SharedTtlCacheRegion<K, V> cast(SharedTtlCacheRegion<?, ?> region) {
    return (SharedTtlCacheRegion<K, V>) region;
  }
}
