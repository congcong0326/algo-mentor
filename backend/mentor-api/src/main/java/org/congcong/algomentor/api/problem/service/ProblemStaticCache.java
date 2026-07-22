package org.congcong.algomentor.api.problem.service;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.congcong.algomentor.api.problem.model.ProblemFilters;
import org.congcong.algomentor.api.problem.model.ProblemLocale;
import org.congcong.algomentor.api.problem.model.ProblemStaticSnapshot;
import org.congcong.algomentor.cache.api.LocalBoundedCacheRegion;
import org.congcong.algomentor.cache.factory.LocalCacheRegionFactory;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.LocalBoundedCacheSpec;

/** 题目和筛选快照的进程内有界缓存门面。 */
final class ProblemStaticCache {

  private static final CacheRegionName STATIC_SNAPSHOT_CACHE_NAME = new CacheRegionName("problem-static-snapshot");
  private static final CacheRegionName FILTERS_CACHE_NAME = new CacheRegionName("problem-filters");

  private final LocalBoundedCacheRegion<String, Optional<ProblemStaticSnapshot>> snapshots;
  private final LocalBoundedCacheRegion<ProblemLocale, ProblemFilters> filters;

  ProblemStaticCache(LocalCacheRegionFactory factory, ProblemCacheProperties properties) {
    Objects.requireNonNull(factory, "factory must not be null");
    Objects.requireNonNull(properties, "properties must not be null");
    snapshots = factory.createBounded(new LocalBoundedCacheSpec(
        STATIC_SNAPSHOT_CACHE_NAME, properties.getStaticSnapshotMaximumSize()));
    filters = factory.createBounded(new LocalBoundedCacheSpec(
        FILTERS_CACHE_NAME, properties.getFiltersMaximumSize()));
  }

  Optional<ProblemStaticSnapshot> getSnapshot(
      String slug,
      Supplier<Optional<ProblemStaticSnapshot>> loader) {
    Objects.requireNonNull(loader, "loader must not be null");
    return snapshots.get(normalizeSlug(slug), ignored -> Objects.requireNonNull(
        loader.get(), "loader result must not be null"));
  }

  ProblemFilters getFilters(ProblemLocale locale, Supplier<ProblemFilters> loader) {
    Objects.requireNonNull(locale, "locale must not be null");
    Objects.requireNonNull(loader, "loader must not be null");
    return filters.get(locale, ignored -> Objects.requireNonNull(loader.get(), "loader result must not be null"));
  }

  static String normalizeSlug(String slug) {
    if (slug == null || slug.isBlank()) {
      throw new IllegalArgumentException("problemSlug must not be blank");
    }
    return slug.trim().toLowerCase(Locale.ROOT);
  }
}
