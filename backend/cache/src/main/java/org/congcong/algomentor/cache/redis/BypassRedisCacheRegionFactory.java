package org.congcong.algomentor.cache.redis;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.congcong.algomentor.cache.api.RedisTtlCacheRegion;
import org.congcong.algomentor.cache.codec.RedisValueCodec;
import org.congcong.algomentor.cache.factory.RedisCacheRegionFactory;
import org.congcong.algomentor.cache.registry.CacheRegionDefinition;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;

/** Redis 或全局缓存关闭时的无状态旁路 factory。 */
public final class BypassRedisCacheRegionFactory implements RedisCacheRegionFactory {

  private final CacheRegionRegistry registry;
  private final Map<CacheRegionName, RedisTtlCacheSpec> specifications = new ConcurrentHashMap<>();
  private final Map<CacheRegionName, SharedCacheKeyCodec<?>> keyCodecs = new ConcurrentHashMap<>();
  private final Map<CacheRegionName, RedisValueCodec<?>> valueCodecs = new ConcurrentHashMap<>();
  private final Map<CacheRegionName, RedisTtlCacheRegion<?, ?>> regions = new ConcurrentHashMap<>();

  public BypassRedisCacheRegionFactory(CacheRegionRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  @Override
  public <K, V> RedisTtlCacheRegion<K, V> createTtl(
      RedisTtlCacheSpec specification,
      SharedCacheKeyCodec<K> keyCodec,
      RedisValueCodec<V> valueCodec) {
    validateAndRegister(specification, keyCodec, valueCodec);
    RedisTtlCacheRegion<?, ?> region = regions.computeIfAbsent(
        specification.name(), ignored -> new BypassRedisTtlCacheRegion<>());
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

  private static final class BypassRedisTtlCacheRegion<K, V> implements RedisTtlCacheRegion<K, V> {
    @Override public Optional<V> getIfPresent(K key) {
      Objects.requireNonNull(key, "key must not be null");
      return Optional.empty();
    }

    @Override public V get(K key, Function<? super K, ? extends V> loader) {
      Objects.requireNonNull(key, "key must not be null");
      return Objects.requireNonNull(Objects.requireNonNull(loader, "loader must not be null").apply(key),
          "loader result must not be null");
    }

    @Override public void put(K key, V value) {
      Objects.requireNonNull(key, "key must not be null");
      Objects.requireNonNull(value, "value must not be null");
    }

    @Override public void invalidate(K key) { Objects.requireNonNull(key, "key must not be null"); }
  }
}
