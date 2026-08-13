package org.congcong.algomentor.queue.model;

import java.time.Instant;

/** 已持久化队列消息，value 保持为队列不解析的 JSON 文本。 */
public record QueueMessage(long messageId, String topic, String key, String value, Instant createdAt, int deliveryAttempt) {

  public QueueMessage(long messageId, String topic, String key, String value, Instant createdAt) {
    this(messageId, topic, key, value, createdAt, 0);
  }

  public QueueMessage {
    if (messageId < 1 || topic == null || topic.isBlank() || key == null || key.isBlank()
        || value == null || createdAt == null || deliveryAttempt < 0) {
      throw new IllegalArgumentException("Invalid persistent queue message");
    }
  }
}
