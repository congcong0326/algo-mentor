package org.congcong.algomentor.queue;

/** 持久化队列跨模块稳定契约。 */
public final class PersistentQueueConstants {

  public static final String CONFIG_PREFIX = "algo-mentor.queue";
  public static final String CONFIG_CONSUMER_ENABLED = CONFIG_PREFIX + ".consumer.enabled";
  public static final String CONFIG_CONSUMER_POLL_INTERVAL = CONFIG_PREFIX + ".consumer.poll-interval";
  public static final String CONFIG_CONSUMER_SHUTDOWN_TIMEOUT = CONFIG_PREFIX + ".consumer.shutdown-timeout";
  public static final String CONFIG_CONSUMER_LEASE_DURATION = CONFIG_PREFIX + ".consumer.lease-duration";
  public static final String CONFIG_CONSUMER_MAX_ATTEMPTS = CONFIG_PREFIX + ".consumer.max-attempts";
  public static final String CONFIG_CONSUMER_RETRY_INITIAL_BACKOFF = CONFIG_PREFIX + ".consumer.retry-initial-backoff";
  public static final String CONFIG_CONSUMER_RETRY_MAX_BACKOFF = CONFIG_PREFIX + ".consumer.retry-max-backoff";
  public static final String CONFIG_CLEANUP_SUCCEEDED_RETENTION = CONFIG_PREFIX + ".cleanup.succeeded-retention";
  public static final String CONFIG_CLEANUP_FIXED_DELAY = CONFIG_PREFIX + ".cleanup.fixed-delay";
  public static final String CONFIG_CLEANUP_BATCH_SIZE = CONFIG_PREFIX + ".cleanup.batch-size";
  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_PROCESSING = "PROCESSING";
  public static final String STATUS_SUCCEEDED = "SUCCEEDED";
  public static final String STATUS_FAILED = "FAILED";
  public static final String FIELD_MESSAGE_ID = "messageId";
  public static final String FIELD_TOPIC = "topic";
  public static final String FIELD_KEY = "key";
  public static final String FIELD_VALUE = "value";
  public static final String FIELD_CREATED_AT = "createdAt";
  public static final int MAX_TOPIC_LENGTH = 128;
  public static final int MAX_KEY_LENGTH = 256;
  public static final int DEFAULT_MAX_VALUE_BYTES = 65_536;
  public static final int DEFAULT_CLEANUP_BATCH_SIZE = 1_000;
  public static final int DEFAULT_CONSUMER_MAX_ATTEMPTS = 5;

  private PersistentQueueConstants() {
  }
}
