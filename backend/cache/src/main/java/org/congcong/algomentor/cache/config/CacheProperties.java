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

  public static class Coherence {

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
      this.pollInterval = requirePositive(pollInterval, "pollInterval");
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      if (batchSize <= 0) {
        throw new IllegalArgumentException("batchSize must be greater than zero");
      }
      this.batchSize = batchSize;
    }

    public double getJitterRatio() {
      return jitterRatio;
    }

    public void setJitterRatio(double jitterRatio) {
      if (jitterRatio < 0 || jitterRatio >= 1) {
        throw new IllegalArgumentException("jitterRatio must be between 0 (inclusive) and 1 (exclusive)");
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
