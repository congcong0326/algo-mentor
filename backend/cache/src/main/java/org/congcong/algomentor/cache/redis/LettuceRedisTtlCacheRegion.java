package org.congcong.algomentor.cache.redis;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.congcong.algomentor.cache.api.RedisTtlCacheRegion;
import org.congcong.algomentor.cache.codec.RedisValueCodec;
import org.congcong.algomentor.cache.codec.RedisValueCodecException;
import org.congcong.algomentor.cache.metrics.CacheInvalidationType;
import org.congcong.algomentor.cache.metrics.CacheLoadResult;
import org.congcong.algomentor.cache.metrics.CacheMetrics;
import org.congcong.algomentor.cache.metrics.CacheOperationResult;
import org.congcong.algomentor.cache.metrics.CacheRequestResult;
import org.congcong.algomentor.cache.metrics.RedisCacheFailureReason;
import org.congcong.algomentor.cache.metrics.RedisCacheMetrics;
import org.congcong.algomentor.cache.metrics.RedisCacheOperation;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;

/** Redis cache-aside region；Redis 和 codec 故障一律旁路，loader 异常原样传播。 */
final class LettuceRedisTtlCacheRegion<K, V> implements RedisTtlCacheRegion<K, V> {

  private final RedisTtlCacheSpec specification;
  private final SharedCacheKeyCodec<K> keyCodec;
  private final RedisValueCodec<V> valueCodec;
  private final RedisCacheKeyBuilder keyBuilder;
  private final RedisConnectionManager connectionManager;
  private final int maxValueBytes;
  private final CacheMetrics cacheMetrics;
  private final RedisCacheMetrics redisMetrics;

  LettuceRedisTtlCacheRegion(
      RedisTtlCacheSpec specification,
      SharedCacheKeyCodec<K> keyCodec,
      RedisValueCodec<V> valueCodec,
      RedisCacheKeyBuilder keyBuilder,
      RedisConnectionManager connectionManager,
      int maxValueBytes,
      CacheMetrics cacheMetrics,
      RedisCacheMetrics redisMetrics) {
    this.specification = Objects.requireNonNull(specification, "specification must not be null");
    this.keyCodec = Objects.requireNonNull(keyCodec, "keyCodec must not be null");
    this.valueCodec = Objects.requireNonNull(valueCodec, "valueCodec must not be null");
    this.keyBuilder = Objects.requireNonNull(keyBuilder, "keyBuilder must not be null");
    this.connectionManager = Objects.requireNonNull(connectionManager, "connectionManager must not be null");
    this.maxValueBytes = maxValueBytes;
    this.cacheMetrics = Objects.requireNonNull(cacheMetrics, "cacheMetrics must not be null");
    this.redisMetrics = Objects.requireNonNull(redisMetrics, "redisMetrics must not be null");
  }

  @Override
  public Optional<V> getIfPresent(K key) {
    String physicalKey = physicalKey(key);
    byte[] value;
    try {
      value = command(RedisCacheOperation.GET, () -> commands().get(bytes(physicalKey)));
    } catch (RuntimeException exception) {
      recordFailure(RedisCacheOperation.GET, exception);
      cacheMetrics.recordRequest(specification.name(), CacheRequestResult.MISS);
      return Optional.empty();
    }
    if (value == null) {
      cacheMetrics.recordRequest(specification.name(), CacheRequestResult.MISS);
      return Optional.empty();
    }
    try {
      V decoded = valueCodec.decode(value);
      cacheMetrics.recordRequest(specification.name(), CacheRequestResult.HIT);
      return Optional.of(decoded);
    } catch (RuntimeException exception) {
      recordFailure(RedisCacheOperation.GET, exception);
      bestEffortDelete(physicalKey);
      cacheMetrics.recordRequest(specification.name(), CacheRequestResult.MISS);
      return Optional.empty();
    }
  }

  @Override
  public V get(K key, Function<? super K, ? extends V> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    Optional<V> cached = getIfPresent(key);
    if (cached.isPresent()) {
      return cached.get();
    }
    V value = load(key, loader);
    put(key, value);
    return value;
  }

  @Override
  public void put(K key, V value) {
    String physicalKey = physicalKey(key);
    byte[] encoded;
    try {
      encoded = valueCodec.encode(Objects.requireNonNull(value, "value must not be null"));
    } catch (RuntimeException exception) {
      recordFailure(RedisCacheOperation.SET, exception);
      return;
    }
    if (encoded.length > maxValueBytes) {
      redisMetrics.recordFailure(specification.name(), RedisCacheOperation.SET, RedisCacheFailureReason.OVERSIZE);
      redisMetrics.recordOversizeValue(specification.name());
      return;
    }
    try {
      command(RedisCacheOperation.SET, () -> commands().set(
          bytes(physicalKey), encoded, SetArgs.Builder.px(specification.ttl().toMillis())));
    } catch (RuntimeException exception) {
      recordFailure(RedisCacheOperation.SET, exception);
    }
  }

  @Override
  public void invalidate(K key) {
    String physicalKey = physicalKey(key);
    try {
      command(RedisCacheOperation.DEL, () -> commands().del(bytes(physicalKey)));
      cacheMetrics.recordInvalidation(
          specification.name(), CacheInvalidationType.KEY, CacheOperationResult.SUCCESS);
    } catch (RuntimeException exception) {
      recordFailure(RedisCacheOperation.DEL, exception);
      cacheMetrics.recordInvalidation(
          specification.name(), CacheInvalidationType.KEY, CacheOperationResult.FAILURE);
    }
  }

  private String physicalKey(K key) {
    Objects.requireNonNull(key, "key must not be null");
    return keyBuilder.physicalKey(specification, SharedCacheKeyCodec.requireValidToken(keyCodec.encode(key)));
  }

  private V load(K key, Function<? super K, ? extends V> loader) {
    long startedAt = System.nanoTime();
    try {
      V value = Objects.requireNonNull(loader.apply(key), "loader result must not be null");
      cacheMetrics.recordLoad(specification.name(), CacheLoadResult.SUCCESS,
          Duration.ofNanos(System.nanoTime() - startedAt));
      return value;
    } catch (RuntimeException | Error exception) {
      cacheMetrics.recordLoad(specification.name(), CacheLoadResult.FAILURE,
          Duration.ofNanos(System.nanoTime() - startedAt));
      throw exception;
    }
  }

  private void bestEffortDelete(String physicalKey) {
    try {
      command(RedisCacheOperation.DEL, () -> commands().del(bytes(physicalKey)));
    } catch (RuntimeException exception) {
      recordFailure(RedisCacheOperation.DEL, exception);
    }
  }

  private RedisCommands<byte[], byte[]> commands() {
    return connectionManager.commands();
  }

  private <T> T command(RedisCacheOperation operation, RedisCommand<T> command) {
    long startedAt = System.nanoTime();
    try {
      T value = command.execute();
      redisMetrics.recordCommand(specification.name(), operation, CacheOperationResult.SUCCESS,
          Duration.ofNanos(System.nanoTime() - startedAt));
      return value;
    } catch (RuntimeException exception) {
      redisMetrics.recordCommand(specification.name(), operation, CacheOperationResult.FAILURE,
          Duration.ofNanos(System.nanoTime() - startedAt));
      throw exception;
    }
  }

  private void recordFailure(RedisCacheOperation operation, RuntimeException exception) {
    redisMetrics.recordFailure(specification.name(), operation, failureReason(exception));
  }

  private static RedisCacheFailureReason failureReason(Throwable exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current instanceof RedisValueCodecException) {
        return RedisCacheFailureReason.CODEC;
      }
      if (current instanceof RedisCommandTimeoutException || current instanceof TimeoutException) {
        return RedisCacheFailureReason.TIMEOUT;
      }
      if (current instanceof RedisConnectionException) {
        return RedisCacheFailureReason.CONNECTION;
      }
    }
    return RedisCacheFailureReason.COMMAND;
  }

  private static byte[] bytes(String value) {
    return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  @FunctionalInterface
  private interface RedisCommand<T> {
    T execute();
  }
}
