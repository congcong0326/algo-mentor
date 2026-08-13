package org.congcong.algomentor.cache.metrics;

/** 缓存模块对外暴露的稳定 Micrometer 指标名。 */
public final class CacheMetricNames {

  public static final String REQUESTS = "algo_mentor_cache_requests_total";
  public static final String LOADS = "algo_mentor_cache_loads_total";
  public static final String LOAD_DURATION = "algo_mentor_cache_load_duration_seconds";
  public static final String INVALIDATIONS = "algo_mentor_cache_invalidations_total";
  public static final String EVICTIONS = "algo_mentor_cache_evictions_total";
  public static final String ESTIMATED_SIZE = "algo_mentor_cache_estimated_size";
  public static final String COHERENCE_EVENTS_PUBLISHED =
      "algo_mentor_cache_coherence_events_published_total";
  public static final String COHERENCE_POLLS = "algo_mentor_cache_coherence_polls_total";
  public static final String COHERENCE_EVENTS_CONSUMED =
      "algo_mentor_cache_coherence_events_consumed_total";
  public static final String COHERENCE_POLL_LAG = "algo_mentor_cache_coherence_poll_lag_seconds";
  public static final String COHERENCE_LAST_SUCCESS_AGE =
      "algo_mentor_cache_coherence_last_success_age_seconds";
  public static final String COHERENCE_GAP_RECOVERIES =
      "algo_mentor_cache_coherence_gap_recoveries_total";
  public static final String COHERENCE_GENERATION_RETRIES =
      "algo_mentor_cache_coherence_generation_retries_total";
  public static final String COHERENCE_CLEANUP = "algo_mentor_cache_coherence_cleanup_total";
  public static final String REDIS_COMMANDS = "algo_mentor_cache_redis_commands_total";
  public static final String REDIS_COMMAND_DURATION = "algo_mentor_cache_redis_command_duration_seconds";
  public static final String REDIS_FAILURES = "algo_mentor_cache_redis_failures_total";
  public static final String REDIS_OVERSIZE_VALUES = "algo_mentor_cache_redis_oversize_values_total";

  private CacheMetricNames() {
  }
}
