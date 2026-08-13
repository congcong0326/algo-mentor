package org.congcong.algomentor.cache.redis;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.cache.api.RedisTtlCacheRegion;
import org.congcong.algomentor.cache.codec.RedisValueCodec;
import org.congcong.algomentor.cache.factory.RedisCacheRegionFactory;
import org.congcong.algomentor.cache.metrics.CacheMetrics;
import org.congcong.algomentor.cache.metrics.RedisCacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionDefinition;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;

/** Lettuce Redis-only region factory；不注册 Shared PostgreSQL 失效目标。 */
public final class LettuceRedisCacheRegionFactory implements RedisCacheRegionFactory {

  private final CacheRegionRegistry registry;
  private final RedisConnectionManager connectionManager;
  private final int maxValueBytes;
  private final CacheMetrics cacheMetrics;
  private final RedisCacheMetrics redisMetrics;
  private final RedisCacheKeyBuilder keyBuilder = new RedisCacheKeyBuilder();
  private final Map<CacheRegionName, RedisTtlCacheSpec> specifications = new ConcurrentHashMap<>();
  private final Map<CacheRegionName, SharedCacheKeyCodec<?>> keyCodecs = new ConcurrentHashMap<>();
  private final Map<CacheRegionName, RedisValueCodec<?>> valueCodecs = new ConcurrentHashMap<>();
  private final Map<CacheRegionName, RedisTtlCacheRegion<?, ?>> regions = new ConcurrentHashMap<>();

  public LettuceRedisCacheRegionFactory(
      CacheRegionRegistry registry,
      RedisConnectionManager connectionManager,
      int maxValueBytes,
      CacheMetrics cacheMetrics,
      RedisCacheMetrics redisMetrics) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
    this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
    if (maxValueBytes < 1) {
      throw new IllegalArgumentException("maxValueBytes must be positive");
    }
    this.maxValueBytes = maxValueBytes;
    this.cacheMetrics = Objects.requireNonNull(cacheMetrics, "cacheMetrics must not be null");
    this.redisMetrics = Objects.requireNonNull(redisMetrics, "redisMetrics must not be null");
  }

  @Override
  public <K, V> RedisTtlCacheRegion<K, V> createTtl(
      RedisTtlCacheSpec specification,
      SharedCacheKeyCodec<K> keyCodec,
      RedisValueCodec<V> valueCodec) {
    validateAndRegister(specification, keyCodec, valueCodec);
    RedisTtlCacheRegion<?, ?> region = regions.computeIfAbsent(specification.name(), ignored ->
        new LettuceRedisTtlCacheRegion<>(specification, keyCodec, valueCodec, keyBuilder,
            connectionManager, maxValueBytes, cacheMetrics, redisMetrics));
    return cast(region);
  }

  private void validateAndRegister(RedisTtlCacheSpec specification, SharedCacheKeyCodec<?> keyCodec,
      RedisValueCodec<?> valueCodec) {
    Objects.requireNonNull(specification, "specification must not be null");
    Objects.requireNonNull(keyCodec, "keyCodec must not be null");
    Objects.requireNonNull(valueCodec, "valueCodec must not be null");
    registry.register(CacheRegionDefinition.redisTtl(specification));
    requireEqual(specifications, specification.name(), specification, "specification");
    requireSameInstance(keyCodecs, specification.name(), keyCodec, "key codec");
    requireSameInstance(valueCodecs, specification.name(), valueCodec, "value codec");
  }

  private static <T> void requireEqual(Map<CacheRegionName, T> values, CacheRegionName name,
      T value, String label) {
    T existing = values.putIfAbsent(name, value);
    if (existing != null && !existing.equals(value)) {
      throw new IllegalStateException("Redis cache '" + name.value()
          + "' was already created with a different " + label);
    }
  }

  private static <T> void requireSameInstance(Map<CacheRegionName, T> values, CacheRegionName name,
      T value, String label) {
    T existing = values.putIfAbsent(name, value);
    if (existing != null && existing != value) {
      throw new IllegalStateException("Redis cache '" + name.value()
          + "' was already created with a different " + label);
    }
  }

  @SuppressWarnings("unchecked")
  private static <K, V> RedisTtlCacheRegion<K, V> cast(RedisTtlCacheRegion<?, ?> region) {
    return (RedisTtlCacheRegion<K, V>) region;
  }
}
