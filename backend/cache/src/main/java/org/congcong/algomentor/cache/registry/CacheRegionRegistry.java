package org.congcong.algomentor.cache.registry;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.congcong.algomentor.cache.api.LocalCacheRegion;
import org.congcong.algomentor.cache.spec.CacheRegionName;

public final class CacheRegionRegistry {

  private final Map<CacheRegionName, CacheRegionDefinition> definitions = new ConcurrentHashMap<>();
  private final Map<SharedCacheIdentity, CacheRegionName> sharedNames = new ConcurrentHashMap<>();
  private final Map<CacheRegionName, LocalCacheRegion<?, ?>> localRegions = new ConcurrentHashMap<>();
  private final Map<CacheRegionName, CacheInvalidationTarget> sharedRegions = new ConcurrentHashMap<>();

  public synchronized void register(CacheRegionDefinition definition) {
    Objects.requireNonNull(definition, "definition must not be null");
    CacheRegionDefinition existing = definitions.get(definition.name());
    if (existing != null && !existing.equals(definition)) {
      throw new IllegalStateException(
          "Cache region '" + definition.name().value() + "' was already registered as " + existing);
    }
    if (definition.sharedIdentity() != null) {
      CacheRegionName existingName = sharedNames.get(definition.sharedIdentity());
      if (existingName != null && !existingName.equals(definition.name())) {
        throw new IllegalStateException(
            "Shared cache namespace '" + definition.sharedIdentity().namespace()
                + "' schema version " + definition.sharedIdentity().schemaVersion()
                + " was already registered by cache '" + existingName.value() + "'");
      }
      sharedNames.putIfAbsent(definition.sharedIdentity(), definition.name());
    }
    definitions.putIfAbsent(definition.name(), definition);
  }

  public Optional<CacheRegionDefinition> find(CacheRegionName name) {
    return Optional.ofNullable(definitions.get(Objects.requireNonNull(name, "name must not be null")));
  }

  /**
   * Registers a node-local cache instance so a completed database restore can remove stale values.
   */
  public void registerLocalRegion(CacheRegionName name, LocalCacheRegion<?, ?> region) {
    registerTarget(localRegions, Objects.requireNonNull(name, "name must not be null"),
        Objects.requireNonNull(region, "region must not be null"));
  }

  /**
   * Registers a shared cache's local invalidation target without exposing cache keys or values.
   */
  public void registerSharedRegion(CacheRegionName name, Object target, Runnable invalidateAll) {
    Objects.requireNonNull(invalidateAll, "invalidateAll must not be null");
    CacheInvalidationTarget resolved = new CacheInvalidationTarget(
        Objects.requireNonNull(target, "target must not be null"), invalidateAll);
    CacheInvalidationTarget existing = sharedRegions.putIfAbsent(
        Objects.requireNonNull(name, "name must not be null"), resolved);
    if (existing != null && existing.target() != target) {
      throw new IllegalStateException("Shared cache invalidation target already registered: " + name.value());
    }
  }

  /** Clears every cache instance held by this JVM. */
  public void invalidateAllRegions() {
    RuntimeException failure = null;
    for (LocalCacheRegion<?, ?> region : localRegions.values()) {
      try {
        region.invalidateAll();
      } catch (RuntimeException exception) {
        failure = append(failure, exception);
      }
    }
    for (CacheInvalidationTarget target : sharedRegions.values()) {
      try {
        target.invalidateAll().run();
      } catch (RuntimeException exception) {
        failure = append(failure, exception);
      }
    }
    if (failure != null) {
      throw failure;
    }
  }

  private static <T> void registerTarget(Map<CacheRegionName, T> regions, CacheRegionName name, T target) {
    T existing = regions.putIfAbsent(name, target);
    if (existing != null && existing != target) {
      throw new IllegalStateException("Cache invalidation target already registered: " + name.value());
    }
  }

  private static RuntimeException append(RuntimeException current, RuntimeException next) {
    if (current == null) {
      return next;
    }
    current.addSuppressed(next);
    return current;
  }

  private record CacheInvalidationTarget(Object target, Runnable invalidateAll) {
  }
}
