package org.congcong.algomentor.queue.postgres;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface QueueMessageMapper {

  QueueMessageRow insertPending(@Param("topic") String topic, @Param("key") String key, @Param("value") String value);

  QueueMessageRow findById(@Param("messageId") long messageId);

  List<QueueMessageRow> findPendingByTopicAndKey(@Param("topic") String topic, @Param("key") String key);

  List<QueueMessageRow> findPendingByTopicAndKeyLimited(
      @Param("topic") String topic, @Param("key") String key, @Param("limit") int limit);

  List<org.congcong.algomentor.queue.dispatch.QueueEligibleKey> findEligibleKeys(
      @Param("topic") String topic, @Param("batchSize") int batchSize);

  int markSucceeded(@Param("messageIds") List<Long> messageIds);

  long countPendingByTopic(@Param("topic") String topic);

  Instant findOldestPendingCreatedAt(@Param("topic") String topic);

  int deleteSucceededBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
