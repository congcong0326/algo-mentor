package org.congcong.algomentor.queue.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.dispatch.QueueEligibleKey;

/** 队列消息的存储端口；派发能力在 LP-06 扩展。 */
public interface QueueMessageRepository {

  QueueMessage insertPending(String topic, String key, String value);

  Optional<QueueMessage> findById(long messageId);

  List<QueueMessage> findPendingByTopicAndKey(String topic, String key, int limit);

  List<QueueEligibleKey> findEligibleKeys(String topic, int batchSize);

  int reclaimExpiredProcessing(Instant now);

  int claimPending(List<Long> messageIds, UUID leaseToken, Instant leaseExpiresAt, Instant now);

  int markSucceeded(List<Long> messageIds, UUID leaseToken);

  int retryOrFail(
      List<Long> messageIds,
      UUID leaseToken,
      Instant retryAt,
      Instant now,
      int maxAttempts,
      String errorType);

  long countFailedByTopic(String topic);

  long countPendingByTopic(String topic);

  Optional<Instant> findOldestPendingCreatedAt(String topic);

  int deleteSucceededBefore(Instant cutoff, int limit);
}
