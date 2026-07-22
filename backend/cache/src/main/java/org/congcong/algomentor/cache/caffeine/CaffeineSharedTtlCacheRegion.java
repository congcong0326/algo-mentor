package org.congcong.algomentor.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.congcong.algomentor.cache.api.SharedTtlCacheRegion;
import org.congcong.algomentor.cache.coherence.SharedCacheRegionDescriptor;
import org.congcong.algomentor.cache.metrics.CacheEvictionCause;
import org.congcong.algomentor.cache.metrics.CacheInvalidationType;
import org.congcong.algomentor.cache.metrics.CacheLoadResult;
import org.congcong.algomentor.cache.metrics.CacheMetrics;
import org.congcong.algomentor.cache.metrics.CacheOperationResult;
import org.congcong.algomentor.cache.metrics.CacheRequestResult;
import org.congcong.algomentor.cache.metrics.CacheCoherenceMetrics;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;

/** Caffeine value store with local generation fencing for shared invalidation events. */
public final class CaffeineSharedTtlCacheRegion<K, V>
    implements SharedTtlCacheRegion<K, V>, SharedCacheRegionDescriptor<K, V> {

  private final SharedTtlCacheSpec specification;
  private final SharedCacheKeyCodec<K> keyCodec;
  private final Cache<String, VersionedValue<V>> cache;
  private final CacheMetrics metrics;
  private final CacheCoherenceMetrics coherenceMetrics;
  private final ConcurrentMap<String, GenerationState> generations = new ConcurrentHashMap<>();
  private final AtomicLong regionGeneration = new AtomicLong();

  public CaffeineSharedTtlCacheRegion(
      SharedTtlCacheSpec specification,
      SharedCacheKeyCodec<K> keyCodec,
      Ticker ticker,
      CacheMetrics metrics) {
    this(specification, keyCodec, ticker, metrics, CacheCoherenceMetrics.noop());
  }

  public CaffeineSharedTtlCacheRegion(
      SharedTtlCacheSpec specification,
      SharedCacheKeyCodec<K> keyCodec,
      Ticker ticker,
      CacheMetrics metrics,
      CacheCoherenceMetrics coherenceMetrics) {
    this.specification = Objects.requireNonNull(specification, "specification must not be null");
    this.keyCodec = Objects.requireNonNull(keyCodec, "keyCodec must not be null");
    this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    this.coherenceMetrics = Objects.requireNonNull(coherenceMetrics, "coherenceMetrics must not be null");
    this.cache = Caffeine.<String, VersionedValue<V>>newBuilder()
        .maximumSize(specification.maximumSize())
        .expireAfterWrite(specification.ttl())
        .ticker(Objects.requireNonNull(ticker, "ticker must not be null"))
        .removalListener((String keyToken, VersionedValue<V> value, RemovalCause cause) -> {
          metrics.recordEviction(specification.name(), toEvictionCause(cause));
          releaseUnusedGeneration(keyToken);
        })
        .build();
    metrics.registerEstimatedSize(specification.name(), cache::estimatedSize);
  }

  @Override
  public Optional<V> getIfPresent(K key) {
    String keyToken = encodeKey(key);
    VersionedValue<V> value = cache.getIfPresent(keyToken);
    if (value == null || !isCurrent(keyToken, value)) {
      if (value != null) {
        cache.asMap().remove(keyToken, value);
      }
      metrics.recordRequest(specification.name(), CacheRequestResult.MISS);
      return Optional.empty();
    }
    metrics.recordRequest(specification.name(), CacheRequestResult.HIT);
    return Optional.of(value.value());
  }

  @Override
  public V get(K key, Function<? super K, ? extends V> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    String keyToken = encodeKey(key);
    metrics.recordRequest(specification.name(),
        cache.getIfPresent(keyToken) == null ? CacheRequestResult.MISS : CacheRequestResult.HIT);

    while (true) {
      GenerationState state = acquireGeneration(keyToken);
      try {
        VersionedValue<V> existing = cache.getIfPresent(keyToken);
        if (existing != null && isCurrent(state, existing)) {
          return existing.value();
        }
        if (existing != null) {
          cache.asMap().remove(keyToken, existing);
        }
        VersionedValue<V> loaded = cache.get(keyToken,
            ignored -> load(key, keyToken, state, loader));
        if (loaded != null && isCurrent(state, loaded)) {
          return loaded.value();
        }
        coherenceMetrics.recordGenerationRetry(specification.name());
      } finally {
        releaseGeneration(keyToken, state);
      }
    }
  }

  @Override
  public void put(K key, V value) {
    String keyToken = encodeKey(key);
    V resolvedValue = Objects.requireNonNull(value, "value must not be null");
    GenerationState state = acquireGeneration(keyToken);
    try {
      long expectedRegionGeneration = regionGeneration.get();
      long expectedKeyGeneration = state.value.get();
      VersionedValue<V> entry = new VersionedValue<>(
          resolvedValue, expectedRegionGeneration, expectedKeyGeneration);
      cache.put(keyToken, entry);
      if (!isCurrent(state, entry)) {
        cache.asMap().remove(keyToken, entry);
      }
    } finally {
      releaseGeneration(keyToken, state);
    }
  }

  @Override
  public void invalidate(K key) {
    invalidateKeyToken(encodeKey(key));
  }

  @Override
  public SharedTtlCacheSpec specification() {
    return specification;
  }

  @Override
  public String encodeKey(K key) {
    Objects.requireNonNull(key, "key must not be null");
    return SharedCacheKeyCodec.requireValidToken(keyCodec.encode(key));
  }

  @Override
  public void invalidateKeyToken(String keyToken) {
    String resolvedToken = SharedCacheKeyCodec.requireValidToken(keyToken);
    GenerationState state = generations.computeIfAbsent(resolvedToken, ignored -> new GenerationState());
    state.value.incrementAndGet();
    try {
      cache.invalidate(resolvedToken);
      metrics.recordInvalidation(specification.name(), CacheInvalidationType.KEY,
          CacheOperationResult.SUCCESS);
    } catch (RuntimeException exception) {
      metrics.recordInvalidation(specification.name(), CacheInvalidationType.KEY,
          CacheOperationResult.FAILURE);
      throw exception;
    } finally {
      releaseUnusedGeneration(resolvedToken);
    }
  }

  @Override
  public void invalidateAllLocal() {
    regionGeneration.incrementAndGet();
    try {
      cache.invalidateAll();
      metrics.recordInvalidation(specification.name(), CacheInvalidationType.ALL,
          CacheOperationResult.SUCCESS);
    } catch (RuntimeException exception) {
      metrics.recordInvalidation(specification.name(), CacheInvalidationType.ALL,
          CacheOperationResult.FAILURE);
      throw exception;
    }
  }

  void cleanUp() {
    cache.cleanUp();
  }

  long generationCount() {
    return generations.size();
  }

  long generation(String keyToken) {
    GenerationState state = generations.get(SharedCacheKeyCodec.requireValidToken(keyToken));
    return state == null ? 0 : state.value.get();
  }

  private VersionedValue<V> load(
      K key,
      String keyToken,
      GenerationState state,
      Function<? super K, ? extends V> loader) {
    long expectedRegionGeneration = regionGeneration.get();
    long expectedKeyGeneration = state.value.get();
    long startedAt = System.nanoTime();
    try {
      V value = Objects.requireNonNull(loader.apply(key), "loader result must not be null");
      VersionedValue<V> entry = new VersionedValue<>(
          value, expectedRegionGeneration, expectedKeyGeneration);
      metrics.recordLoad(specification.name(), CacheLoadResult.SUCCESS,
          Duration.ofNanos(System.nanoTime() - startedAt));
      return isCurrent(state, entry) ? entry : null;
    } catch (RuntimeException | Error exception) {
      metrics.recordLoad(specification.name(), CacheLoadResult.FAILURE,
          Duration.ofNanos(System.nanoTime() - startedAt));
      throw exception;
    }
  }

  private GenerationState acquireGeneration(String keyToken) {
    GenerationState state = generations.computeIfAbsent(keyToken, ignored -> new GenerationState());
    state.activeOperations.incrementAndGet();
    return state;
  }

  private void releaseGeneration(String keyToken, GenerationState state) {
    state.activeOperations.decrementAndGet();
    releaseUnusedGeneration(keyToken);
  }

  private void releaseUnusedGeneration(String keyToken) {
    GenerationState state = generations.get(keyToken);
    if (state != null && state.activeOperations.get() == 0 && !cache.asMap().containsKey(keyToken)) {
      generations.remove(keyToken, state);
    }
  }

  private boolean isCurrent(String keyToken, VersionedValue<V> value) {
    GenerationState state = generations.get(keyToken);
    return value.regionGeneration() == regionGeneration.get()
        && value.keyGeneration() == (state == null ? 0 : state.value.get());
  }

  private boolean isCurrent(GenerationState state, VersionedValue<V> value) {
    return value.regionGeneration() == regionGeneration.get()
        && value.keyGeneration() == state.value.get();
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

  private record VersionedValue<V>(V value, long regionGeneration, long keyGeneration) {
  }

  private static final class GenerationState {
    private final AtomicLong value = new AtomicLong();
    private final AtomicInteger activeOperations = new AtomicInteger();
  }
}
