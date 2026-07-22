package org.congcong.algomentor.cache.metrics;

/** 缓存模块对外暴露的稳定 Micrometer 指标名。 */
public final class CacheMetricNames {

  public static final String REQUESTS = "algo_mentor_cache_requests_total";
  public static final String LOADS = "algo_mentor_cache_loads_total";
  public static final String LOAD_DURATION = "algo_mentor_cache_load_duration_seconds";
  public static final String INVALIDATIONS = "algo_mentor_cache_invalidations_total";
  public static final String EVICTIONS = "algo_mentor_cache_evictions_total";
  public static final String ESTIMATED_SIZE = "algo_mentor_cache_estimated_size";

  private CacheMetricNames() {
  }
}
