package org.congcong.algomentor.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.congcong.algomentor.cache.metrics.CacheEvictionCause;
import org.congcong.algomentor.cache.metrics.CacheInvalidationType;
import org.congcong.algomentor.cache.metrics.CacheLoadResult;
import org.congcong.algomentor.cache.metrics.CacheMetrics;
import org.congcong.algomentor.cache.metrics.CacheOperationResult;
import org.congcong.algomentor.cache.metrics.CacheRequestResult;
import org.congcong.algomentor.cache.spec.CacheRegionName;

final class CaffeineCacheRegion<K, V> {

  private final CacheRegionName name;
  private final Cache<K, V> cache;
  private final CacheMetrics metrics;

  private CaffeineCacheRegion(CacheRegionName name, Cache<K, V> cache, CacheMetrics metrics) {
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.cache = Objects.requireNonNull(cache, "cache must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    metrics.registerEstimatedSize(name, cache::estimatedSize);
  }

  static <K, V> CaffeineCacheRegion<K, V> create(
      CacheRegionName name,
      long maximumSize,
      Duration ttl,
      Ticker ticker,
      CacheMetrics metrics) {
    Caffeine<K, V> builder = Caffeine.newBuilder()
        .maximumSize(maximumSize)
        .ticker(ticker)
        .removalListener((K key, V value, RemovalCause cause) ->
            metrics.recordEviction(name, toEvictionCause(cause)));
    if (ttl != null) {
      builder.expireAfterWrite(ttl);
    }
    if (metrics.isEnabled()) {
      builder.recordStats();
    }
    return new CaffeineCacheRegion<>(name, builder.build(), metrics);
  }

  Optional<V> getIfPresent(K key) {
    key = requireKey(key);
    V value = cache.getIfPresent(key);
    metrics.recordRequest(name, value == null ? CacheRequestResult.MISS : CacheRequestResult.HIT);
    return Optional.ofNullable(value);
  }

  V get(K key, Function<? super K, ? extends V> loader) {
    K resolvedKey = requireKey(key);
    Objects.requireNonNull(loader, "loader must not be null");
    V cached = cache.asMap().get(resolvedKey);
    metrics.recordRequest(name, cached == null ? CacheRequestResult.MISS : CacheRequestResult.HIT);
    return cache.get(resolvedKey, ignored -> load(resolvedKey, loader));
  }

  void put(K key, V value) {
    cache.put(requireKey(key), Objects.requireNonNull(value, "value must not be null"));
  }

  void invalidate(K key) {
    key = requireKey(key);
    try {
      cache.invalidate(key);
      metrics.recordInvalidation(name, CacheInvalidationType.KEY, CacheOperationResult.SUCCESS);
    } catch (RuntimeException exception) {
      metrics.recordInvalidation(name, CacheInvalidationType.KEY, CacheOperationResult.FAILURE);
      throw exception;
    }
  }

  void invalidateAll() {
    try {
      cache.invalidateAll();
      metrics.recordInvalidation(name, CacheInvalidationType.ALL, CacheOperationResult.SUCCESS);
    } catch (RuntimeException exception) {
      metrics.recordInvalidation(name, CacheInvalidationType.ALL, CacheOperationResult.FAILURE);
      throw exception;
    }
  }

  void cleanUp() {
    cache.cleanUp();
  }

  long estimatedSize() {
    return cache.estimatedSize();
  }

  private V load(K key, Function<? super K, ? extends V> loader) {
    long startedAt = System.nanoTime();
    try {
      V value = Objects.requireNonNull(loader.apply(key), "loader result must not be null");
      metrics.recordLoad(name, CacheLoadResult.SUCCESS, Duration.ofNanos(System.nanoTime() - startedAt));
      return value;
    } catch (RuntimeException | Error exception) {
      metrics.recordLoad(name, CacheLoadResult.FAILURE, Duration.ofNanos(System.nanoTime() - startedAt));
      throw exception;
    }
  }

  private K requireKey(K key) {
    return Objects.requireNonNull(key, "key must not be null");
  }

  private static CacheEvictionCause toEvictionCause(RemovalCause cause) {
    return switch (cause) {
      case COLLECTED -> CacheEvictionCause.COLLECTED;
      case EXPLICIT -> CacheEvictionCause.EXPLICIT;
      case EXPIRED -> CacheEvictionCause.EXPIRED;
      case REPLACED -> CacheEvictionCause.REPLACED;
      case SIZE -> CacheEvictionCause.SIZE;
    };
  }
}
