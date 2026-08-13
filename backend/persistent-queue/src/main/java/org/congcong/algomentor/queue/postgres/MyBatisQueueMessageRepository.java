package org.congcong.algomentor.queue.postgres;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.dispatch.QueueEligibleKey;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;

/** PostgreSQL/MyBatis 队列存储适配。 */
public class MyBatisQueueMessageRepository implements QueueMessageRepository {

  private final QueueMessageMapper mapper;

  public MyBatisQueueMessageRepository(QueueMessageMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public QueueMessage insertPending(String topic, String key, String value) {
    QueueMessageRow row = mapper.insertPending(topic, key, value);
    if (row == null) {
      throw new IllegalStateException("Persistent queue insert did not return a message");
    }
    return toMessage(row);
  }

  @Override
  public Optional<QueueMessage> findById(long messageId) {
    return Optional.ofNullable(mapper.findById(messageId)).map(this::toMessage);
  }

  @Override
  public List<QueueMessage> findPendingByTopicAndKey(String topic, String key, int limit) {
    return mapper.findPendingByTopicAndKeyLimited(topic, key, limit).stream().map(this::toMessage).toList();
  }

  @Override
  public List<QueueEligibleKey> findEligibleKeys(String topic, int batchSize) {
    return mapper.findEligibleKeys(topic, batchSize);
  }

  @Override
  public int reclaimExpiredProcessing(Instant now) {
    return mapper.reclaimExpiredProcessing(now);
  }

  @Override
  public int claimPending(List<Long> messageIds, UUID leaseToken, Instant leaseExpiresAt, Instant now) {
    return messageIds == null || messageIds.isEmpty() ? 0 : mapper.claimPending(messageIds, leaseToken, leaseExpiresAt, now);
  }

  @Override
  public int markSucceeded(List<Long> messageIds, UUID leaseToken) {
    return messageIds == null || messageIds.isEmpty() ? 0 : mapper.markSucceeded(messageIds, leaseToken);
  }

  @Override
  public int retryOrFail(
      List<Long> messageIds,
      UUID leaseToken,
      Instant retryAt,
      Instant now,
      int maxAttempts,
      String errorType) {
    return messageIds == null || messageIds.isEmpty() ? 0
        : mapper.retryOrFail(messageIds, leaseToken, retryAt, now, maxAttempts, errorType);
  }

  @Override
  public long countFailedByTopic(String topic) {
    return mapper.countFailedByTopic(topic);
  }

  @Override
  public long countPendingByTopic(String topic) {
    return mapper.countPendingByTopic(topic);
  }

  @Override
  public Optional<Instant> findOldestPendingCreatedAt(String topic) {
    return Optional.ofNullable(mapper.findOldestPendingCreatedAt(topic));
  }

  @Override
  public int deleteSucceededBefore(Instant cutoff, int limit) {
    return mapper.deleteSucceededBefore(cutoff, limit);
  }

  private QueueMessage toMessage(QueueMessageRow row) {
    return new QueueMessage(row.id(), row.topic(), row.key(), row.value(), row.createdAt(), row.deliveryAttempt());
  }
}
