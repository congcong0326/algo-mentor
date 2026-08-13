package org.congcong.algomentor.queue.dispatch;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 在短事务内领取 PENDING 消息；业务回调在事务外执行，并通过同一 lease token 确认成功或回写失败。
 */
public class QueueDequeueService {

  private final QueueMessageRepository repository;
  private final TransactionTemplate transactionTemplate;
  private final PersistentQueueProperties.Consumer properties;
  private final Clock clock;

  public QueueDequeueService(QueueMessageRepository repository, TransactionTemplate transactionTemplate) {
    this(repository, transactionTemplate, new PersistentQueueProperties().getConsumer());
  }

  public QueueDequeueService(
      QueueMessageRepository repository,
      TransactionTemplate transactionTemplate,
      PersistentQueueProperties.Consumer properties) {
    this(repository, transactionTemplate, properties, Clock.systemUTC());
  }

  QueueDequeueService(
      QueueMessageRepository repository,
      TransactionTemplate transactionTemplate,
      PersistentQueueProperties.Consumer properties,
      Clock clock) {
    this.repository = repository;
    this.transactionTemplate = transactionTemplate;
    this.properties = properties;
    this.clock = clock;
  }

  /** 回收异常退出遗留的过期租约；返回重新可消费的消息数。 */
  public int reclaimExpiredLeases() {
    Instant now = clock.instant();
    Integer reclaimed = transactionTemplate.execute(status -> repository.reclaimExpiredProcessing(now));
    return reclaimed == null ? 0 : reclaimed;
  }

  /** 领取严格数量的同 topic/key 消息，领取失败时不暴露消息给回调。 */
  public QueueDelivery dequeue(String topic, String key, int count) {
    return transactionTemplate.execute(status -> {
      List<QueueMessage> messages = repository.findPendingByTopicAndKey(topic, key, count);
      if (messages.size() != count) {
        status.setRollbackOnly();
        return null;
      }
      Instant now = clock.instant();
      UUID leaseToken = UUID.randomUUID();
      List<Long> messageIds = messages.stream().map(QueueMessage::messageId).toList();
      if (repository.claimPending(messageIds, leaseToken, now.plus(properties.getLeaseDuration()), now) != count) {
        status.setRollbackOnly();
        return null;
      }
      int attempt = messages.stream().mapToInt(QueueMessage::deliveryAttempt).max().orElse(0) + 1;
      List<QueueMessage> delivered = messages.stream()
          .map(message -> new QueueMessage(
              message.messageId(), message.topic(), message.key(), message.value(), message.createdAt(),
              message.deliveryAttempt() + 1))
          .toList();
      return new QueueDelivery(delivered, leaseToken, attempt);
    });
  }

  /** 仅持有当前租约的 worker 可以确认该批消息。 */
  public boolean acknowledge(QueueDelivery delivery) {
    Integer updated = transactionTemplate.execute(status ->
        repository.markSucceeded(delivery.messageIds(), delivery.leaseToken()));
    return updated != null && updated == delivery.messages().size();
  }

  /**
   * 失败后在同一短事务内退避重试或转为 FAILED。达到上限的批次由调用方告警并停止 topic。
   */
  public QueueFailureDisposition fail(QueueDelivery delivery, RuntimeException exception) {
    Instant now = clock.instant();
    int maxAttempt = delivery.messages().stream().mapToInt(QueueMessage::deliveryAttempt).max().orElse(delivery.attempt());
    Duration backoff = retryBackoff(maxAttempt);
    Integer updated = transactionTemplate.execute(status -> repository.retryOrFail(
        delivery.messageIds(), delivery.leaseToken(), now.plus(backoff), now, properties.getMaxAttempts(),
        exception.getClass().getSimpleName()));
    if (updated == null || updated != delivery.messages().size()) {
      throw new IllegalStateException("Persistent queue delivery failure was not recorded");
    }
    return maxAttempt >= properties.getMaxAttempts()
        ? QueueFailureDisposition.TERMINAL_FAILURE
        : QueueFailureDisposition.RETRY_SCHEDULED;
  }

  private Duration retryBackoff(int attempt) {
    long initialMillis = properties.getRetryInitialBackoff().toMillis();
    long maxMillis = properties.getRetryMaxBackoff().toMillis();
    long multiplier = 1L << Math.min(Math.max(0, attempt - 1), 30);
    long backoffMillis = initialMillis > Long.MAX_VALUE / multiplier
        ? maxMillis
        : Math.min(maxMillis, initialMillis * multiplier);
    return Duration.ofMillis(backoffMillis);
  }
}
