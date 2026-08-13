package org.congcong.algomentor.queue.config;

import java.time.Duration;
import org.congcong.algomentor.queue.PersistentQueueConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 持久化队列的存储和运行时配置。 */
@ConfigurationProperties(prefix = PersistentQueueConstants.CONFIG_PREFIX)
public class PersistentQueueProperties {

  private Message message = new Message();
  private Consumer consumer = new Consumer();
  private Cleanup cleanup = new Cleanup();

  public Message getMessage() {
    return message;
  }

  public void setMessage(Message message) {
    this.message = message == null ? new Message() : message;
  }

  public Consumer getConsumer() { return consumer; }

  public void setConsumer(Consumer consumer) { this.consumer = consumer == null ? new Consumer() : consumer; }

  public Cleanup getCleanup() { return cleanup; }

  public void setCleanup(Cleanup cleanup) { this.cleanup = cleanup == null ? new Cleanup() : cleanup; }

  public static class Message {
    private int maxValueBytes = PersistentQueueConstants.DEFAULT_MAX_VALUE_BYTES;

    public int getMaxValueBytes() {
      return maxValueBytes;
    }

    public void setMaxValueBytes(int maxValueBytes) {
      if (maxValueBytes < 1) {
        throw new IllegalArgumentException("algo-mentor.queue.message.max-value-bytes must be positive");
      }
      this.maxValueBytes = maxValueBytes;
    }
  }

  public static class Consumer {
    private boolean enabled;
    private Duration pollInterval = Duration.ofSeconds(10);
    private Duration shutdownTimeout = Duration.ofSeconds(30);
    private Duration leaseDuration = Duration.ofMinutes(10);
    private int maxAttempts = PersistentQueueConstants.DEFAULT_CONSUMER_MAX_ATTEMPTS;
    private Duration retryInitialBackoff = Duration.ofSeconds(30);
    private Duration retryMaxBackoff = Duration.ofMinutes(15);

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Duration getPollInterval() { return pollInterval; }

    public void setPollInterval(Duration pollInterval) {
      this.pollInterval = positiveDuration(pollInterval, PersistentQueueConstants.CONFIG_CONSUMER_POLL_INTERVAL);
    }

    public Duration getShutdownTimeout() { return shutdownTimeout; }

    public void setShutdownTimeout(Duration shutdownTimeout) {
      this.shutdownTimeout = positiveDuration(shutdownTimeout, PersistentQueueConstants.CONFIG_CONSUMER_SHUTDOWN_TIMEOUT);
    }

    public Duration getLeaseDuration() { return leaseDuration; }

    public void setLeaseDuration(Duration leaseDuration) {
      this.leaseDuration = positiveDuration(leaseDuration, PersistentQueueConstants.CONFIG_CONSUMER_LEASE_DURATION);
    }

    public int getMaxAttempts() { return maxAttempts; }

    public void setMaxAttempts(int maxAttempts) {
      if (maxAttempts < 1) {
        throw new IllegalArgumentException(PersistentQueueConstants.CONFIG_CONSUMER_MAX_ATTEMPTS + " must be positive");
      }
      this.maxAttempts = maxAttempts;
    }

    public Duration getRetryInitialBackoff() { return retryInitialBackoff; }

    public void setRetryInitialBackoff(Duration retryInitialBackoff) {
      this.retryInitialBackoff = positiveDuration(
          retryInitialBackoff, PersistentQueueConstants.CONFIG_CONSUMER_RETRY_INITIAL_BACKOFF);
      validateBackoffRange();
    }

    public Duration getRetryMaxBackoff() { return retryMaxBackoff; }

    public void setRetryMaxBackoff(Duration retryMaxBackoff) {
      this.retryMaxBackoff = positiveDuration(retryMaxBackoff, PersistentQueueConstants.CONFIG_CONSUMER_RETRY_MAX_BACKOFF);
      validateBackoffRange();
    }

    private void validateBackoffRange() {
      if (retryInitialBackoff != null && retryMaxBackoff != null && retryMaxBackoff.compareTo(retryInitialBackoff) < 0) {
        throw new IllegalArgumentException(PersistentQueueConstants.CONFIG_CONSUMER_RETRY_MAX_BACKOFF
            + " must not be smaller than " + PersistentQueueConstants.CONFIG_CONSUMER_RETRY_INITIAL_BACKOFF);
      }
    }
  }

  public static class Cleanup {
    private Duration succeededRetention = Duration.ofDays(7);
    private Duration fixedDelay = Duration.ofHours(1);
    private int batchSize = PersistentQueueConstants.DEFAULT_CLEANUP_BATCH_SIZE;

    public Duration getSucceededRetention() { return succeededRetention; }

    public void setSucceededRetention(Duration succeededRetention) {
      this.succeededRetention = positiveDuration(succeededRetention, PersistentQueueConstants.CONFIG_CLEANUP_SUCCEEDED_RETENTION);
    }

    public Duration getFixedDelay() { return fixedDelay; }

    public void setFixedDelay(Duration fixedDelay) {
      this.fixedDelay = positiveDuration(fixedDelay, PersistentQueueConstants.CONFIG_CLEANUP_FIXED_DELAY);
    }

    public int getBatchSize() { return batchSize; }

    public void setBatchSize(int batchSize) {
      if (batchSize < 1) {
        throw new IllegalArgumentException(PersistentQueueConstants.CONFIG_CLEANUP_BATCH_SIZE + " must be positive");
      }
      this.batchSize = batchSize;
    }
  }

  private static Duration positiveDuration(Duration value, String key) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException(key + " must be positive");
    }
    return value;
  }
}
