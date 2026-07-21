package org.congcong.algomentor.queue.runtime;

import java.time.Duration;
import java.time.Instant;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.metrics.QueueMetrics;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.springframework.transaction.support.TransactionTemplate;

/** 在一个短事务内删除一批过期的 SUCCEEDED 消息。 */
public class QueueCleanupService {
  private final QueueMessageRepository repository;
  private final PersistentQueueProperties.Cleanup properties;
  private final TransactionTemplate transactionTemplate;
  private final QueueMetrics metrics;

  public QueueCleanupService(
      QueueMessageRepository repository,
      PersistentQueueProperties.Cleanup properties,
      TransactionTemplate transactionTemplate,
      QueueMetrics metrics) {
    this.repository = repository;
    this.properties = properties;
    this.transactionTemplate = transactionTemplate;
    this.metrics = metrics;
  }

  public QueueCleanupResult cleanupOnce() {
    Instant started = Instant.now();
    int deleted = transactionTemplate.execute(status -> repository.deleteSucceededBefore(
        started.minus(properties.getSucceededRetention()), properties.getBatchSize()));
    QueueCleanupResult result = new QueueCleanupResult(deleted, Duration.between(started, Instant.now()));
    metrics.recordCleanup(result.deletedCount(), result.duration());
    return result;
  }
}
