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
  public static final String REDIS_ENABLED = PREFIX + ".redis.enabled";
  public static final String REDIS_HOST = PREFIX + ".redis.host";
  public static final String REDIS_PORT = PREFIX + ".redis.port";
  public static final String REDIS_DATABASE = PREFIX + ".redis.database";
  public static final String REDIS_USERNAME = PREFIX + ".redis.username";
  public static final String REDIS_PASSWORD = PREFIX + ".redis.password";
  public static final String REDIS_SSL_ENABLED = PREFIX + ".redis.ssl-enabled";
  public static final String REDIS_COMMAND_TIMEOUT = PREFIX + ".redis.command-timeout";
  public static final String REDIS_CONNECT_TIMEOUT = PREFIX + ".redis.connect-timeout";
  public static final String REDIS_SHUTDOWN_TIMEOUT = PREFIX + ".redis.shutdown-timeout";
  public static final String REDIS_MAX_VALUE_BYTES = PREFIX + ".redis.max-value-bytes";

  private CacheConfigurationKeys() {
  }
}
