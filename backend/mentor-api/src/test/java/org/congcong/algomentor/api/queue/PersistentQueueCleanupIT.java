package org.congcong.algomentor.api.queue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.metrics.QueueMetrics;
import org.congcong.algomentor.queue.postgres.MyBatisQueueMessageRepository;
import org.congcong.algomentor.queue.postgres.QueueMessageMapper;
import org.congcong.algomentor.queue.runtime.QueueCleanupResult;
import org.congcong.algomentor.queue.runtime.QueueCleanupService;
import org.junit.jupiter.api.Test;

class PersistentQueueCleanupIT extends PostgresIntegrationTestSupport {

  @Test
  void deletesOnlyOneOrderedBatchOfExpiredSucceededMessages() throws Exception {
    migrateLatest();
    Instant old = Instant.now().minus(Duration.ofDays(8));
    for (int index = 0; index < 1_005; index++) {
      execute("INSERT INTO queue_message (topic, message_key, message_value, status, succeeded_at, created_at) VALUES (?, ?, '{}', 'SUCCEEDED', ?, ?)",
          "cleanup", "old-" + index, Timestamp.from(old.plusMillis(index)), Timestamp.from(old.plusMillis(index)));
    }
    execute("INSERT INTO queue_message (topic, message_key, message_value, status, succeeded_at) VALUES ('cleanup', 'recent', '{}', 'SUCCEEDED', NOW())");
    execute("INSERT INTO queue_message (topic, message_key, message_value, status, succeeded_at) VALUES ('cleanup', 'pending', '{}', 'PENDING', NULL)");

    QueueCleanupResult result = cleanupService().cleanupOnce();

    assertThat(result.deletedCount()).isEqualTo(1_000);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'PENDING'")).isEqualTo(1L);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'SUCCEEDED' AND succeeded_at < NOW() - INTERVAL '7 days'"))
        .isEqualTo(5L);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE message_key = 'recent'")).isEqualTo(1L);
  }

  private QueueCleanupService cleanupService() throws Exception {
    PersistentQueueProperties properties = new PersistentQueueProperties();
    MyBatisQueueMessageRepository repository = new MyBatisQueueMessageRepository(
        sqlSessionTemplate("mapper/queue/QueueMessageMapper.xml").getMapper(QueueMessageMapper.class));
    return new QueueCleanupService(repository, properties.getCleanup(), transactionTemplate(), QueueMetrics.NOOP);
  }
}
