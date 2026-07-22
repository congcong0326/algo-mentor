package org.congcong.algomentor.cache.config;

/** 缓存模块公共配置键。 */
public final class CacheConfigurationKeys {

  public static final String PREFIX = "algo-mentor.cache";
  public static final String ENABLED = PREFIX + ".enabled";
  public static final String METRICS_ENABLED = PREFIX + ".metrics-enabled";
  public static final String SHARED_PROVIDER = PREFIX + ".shared-provider";
  public static final String COHERENCE_ENABLED = PREFIX + ".coherence.enabled";
  public static final String COHERENCE_POLL_INTERVAL = PREFIX + ".coherence.poll-interval";
  public static final String COHERENCE_BATCH_SIZE = PREFIX + ".coherence.batch-size";
  public static final String COHERENCE_JITTER_RATIO = PREFIX + ".coherence.jitter-ratio";
  public static final String COHERENCE_EVENT_RETENTION = PREFIX + ".coherence.event-retention";
  public static final String COHERENCE_CLEANUP_INTERVAL = PREFIX + ".coherence.cleanup-interval";

  private CacheConfigurationKeys() {
  }
}
