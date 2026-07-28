package org.congcong.algomentor.api.databasebackup.service;

import java.util.Objects;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;

/** Clears all cache regions held by the current application instance after a restore commits. */
public final class DatabaseRestoreCacheInvalidator {

  private final CacheRegionRegistry registry;

  public DatabaseRestoreCacheInvalidator(CacheRegionRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry must not be null");
  }

  public static DatabaseRestoreCacheInvalidator noop() {
    return new DatabaseRestoreCacheInvalidator(new CacheRegionRegistry());
  }

  public void invalidateAll() {
    registry.invalidateAllRegions();
  }
}
