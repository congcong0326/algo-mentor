package org.congcong.algomentor.cache.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = CacheConfigurationKeys.PREFIX)
public class CacheProperties {

  private boolean enabled = true;
  private boolean metricsEnabled = true;
  private SharedCacheProvider sharedProvider = SharedCacheProvider.CAFFEINE;
  private Coherence coherence = new Coherence();
  private Redis redis = new Redis();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isMetricsEnabled() {
    return metricsEnabled;
  }

  public void setMetricsEnabled(boolean metricsEnabled) {
    this.metricsEnabled = metricsEnabled;
  }

  public SharedCacheProvider getSharedProvider() {
    return sharedProvider;
  }

  public void setSharedProvider(SharedCacheProvider sharedProvider) {
    this.sharedProvider = Objects.requireNonNull(sharedProvider, "sharedProvider must not be null");
  }

  public Coherence getCoherence() {
    return coherence;
  }

  public void setCoherence(Coherence coherence) {
    this.coherence = Objects.requireNonNull(coherence, "coherence must not be null");
  }

  public Redis getRedis() {
    return redis;
  }

  public void setRedis(Redis redis) {
    this.redis = Objects.requireNonNull(redis, "redis must not be null");
  }

  /** Redis cache 实例连接和单 value 限制；所有敏感字段禁止写入日志。 */
  public static class Redis {

    private static final Duration MAX_COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MAX_CONNECT_OR_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_VALUE_BYTES = 65_536;

    private boolean enabled = true;
    private String host = "localhost";
    private int port = 6379;
    private int database;
    private String username = "";
    private String password = "";
    private boolean sslEnabled;
    private Duration commandTimeout = Duration.ofMillis(100);
    private Duration connectTimeout = Duration.ofSeconds(1);
    private Duration shutdownTimeout = Duration.ofSeconds(2);
    private int maxValueBytes = MAX_VALUE_BYTES;

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getHost() { return host; }

    public void setHost(String host) {
      if (host == null || host.isBlank()) {
        throw new IllegalArgumentException("redis host must not be blank");
      }
      this.host = host;
    }

    public int getPort() { return port; }

    public void setPort(int port) {
      if (port < 1 || port > 65_535) {
        throw new IllegalArgumentException("redis port must be between 1 and 65535");
      }
      if (port == 6380) {
        throw new IllegalArgumentException("redis port 6380 is reserved for Redis Streams");
      }
      this.port = port;
    }

    public int getDatabase() { return database; }

    public void setDatabase(int database) {
      if (database < 0 || database > 15) {
        throw new IllegalArgumentException("redis database must be between 0 and 15");
      }
      this.database = database;
    }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username == null ? "" : username; }

    public String getPassword() { return password; }

    public void setPassword(String password) { this.password = password == null ? "" : password; }

    public boolean isSslEnabled() { return sslEnabled; }

    public void setSslEnabled(boolean sslEnabled) { this.sslEnabled = sslEnabled; }

    public Duration getCommandTimeout() { return commandTimeout; }

    public void setCommandTimeout(Duration commandTimeout) {
      this.commandTimeout = requireDurationAtMost(
          commandTimeout, "redis commandTimeout", MAX_COMMAND_TIMEOUT);
    }

    public Duration getConnectTimeout() { return connectTimeout; }

    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = requireDurationAtMost(
          connectTimeout, "redis connectTimeout", MAX_CONNECT_OR_SHUTDOWN_TIMEOUT);
    }

    public Duration getShutdownTimeout() { return shutdownTimeout; }

    public void setShutdownTimeout(Duration shutdownTimeout) {
      this.shutdownTimeout = requireDurationAtMost(
          shutdownTimeout, "redis shutdownTimeout", MAX_CONNECT_OR_SHUTDOWN_TIMEOUT);
    }

    public int getMaxValueBytes() { return maxValueBytes; }

    public void setMaxValueBytes(int maxValueBytes) {
      if (maxValueBytes < 1 || maxValueBytes > MAX_VALUE_BYTES) {
        throw new IllegalArgumentException("redis maxValueBytes must be between 1 and 65536");
      }
      this.maxValueBytes = maxValueBytes;
    }

    private static Duration requireDurationAtMost(Duration value, String name, Duration maximum) {
      value = Objects.requireNonNull(value, name + " must not be null");
      if (value.isZero() || value.isNegative() || value.compareTo(maximum) > 0) {
        throw new IllegalArgumentException(name + " must be positive and no more than " + maximum);
      }
      return value;
    }
  }

  public static class Coherence {

    private static final Duration MIN_POLL_INTERVAL = Duration.ofMillis(250);
    private static final int MAX_BATCH_SIZE = 5_000;
    private static final double MAX_JITTER_RATIO = 0.5;

    private boolean enabled = true;
    private Duration pollInterval = Duration.ofSeconds(1);
    private int batchSize = 500;
    private double jitterRatio = 0.2;
    private Duration eventRetention = Duration.ofHours(24);
    private Duration cleanupInterval = Duration.ofHours(1);

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public Duration getPollInterval() {
      return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
      Duration value = requirePositive(pollInterval, "pollInterval");
      if (value.compareTo(MIN_POLL_INTERVAL) < 0) {
        throw new IllegalArgumentException("pollInterval must be at least " + MIN_POLL_INTERVAL);
      }
      this.pollInterval = value;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
        throw new IllegalArgumentException("batchSize must be between 1 and " + MAX_BATCH_SIZE);
      }
      this.batchSize = batchSize;
    }

    public double getJitterRatio() {
      return jitterRatio;
    }

    public void setJitterRatio(double jitterRatio) {
      if (jitterRatio < 0 || jitterRatio > MAX_JITTER_RATIO) {
        throw new IllegalArgumentException(
            "jitterRatio must be between 0 and " + MAX_JITTER_RATIO + " (inclusive)");
      }
      this.jitterRatio = jitterRatio;
    }

    public Duration getEventRetention() {
      return eventRetention;
    }

    public void setEventRetention(Duration eventRetention) {
      this.eventRetention = requirePositive(eventRetention, "eventRetention");
    }

    public Duration getCleanupInterval() {
      return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
      this.cleanupInterval = requirePositive(cleanupInterval, "cleanupInterval");
    }

    private static Duration requirePositive(Duration duration, String fieldName) {
      Objects.requireNonNull(duration, fieldName + " must not be null");
      if (duration.isZero() || duration.isNegative()) {
        throw new IllegalArgumentException(fieldName + " must be positive");
      }
      return duration;
    }
  }
}
