package org.congcong.algomentor.queue.postgres;

import java.time.Instant;

/** queue_message 的数据库行模型。 */
public record QueueMessageRow(long id, String topic, String key, String value, Instant createdAt, int deliveryAttempt) {
}
