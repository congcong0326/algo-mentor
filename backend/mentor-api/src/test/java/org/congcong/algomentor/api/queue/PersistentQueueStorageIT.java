package org.congcong.algomentor.api.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.postgres.MyBatisQueueMessageRepository;
import org.congcong.algomentor.queue.postgres.QueueMessageMapper;
import org.congcong.algomentor.queue.publisher.PostgresQueuePublisher;
import org.junit.jupiter.api.Test;

class PersistentQueueStorageIT extends PostgresIntegrationTestSupport {

  @Test
  void persistsOnlyPendingMessagesAndParticipatesInTheCallerTransaction() throws Exception {
    migrateLatest();
    PostgresQueuePublisher publisher = publisher();

    QueueMessage message = publisher.publish("learner-profile.test", "42", new Payload(7L));

    assertThat(message.messageId()).isPositive();
    assertThat(queryString("SELECT status FROM queue_message WHERE id = ?", message.messageId()))
        .isEqualTo("PENDING");
    assertThat(queryString("SELECT succeeded_at::text FROM queue_message WHERE id = ?", message.messageId()))
        .isNull();
    assertThat(queryLong("SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname IN "
        + "('idx_queue_message_pending_topic', 'idx_queue_message_pending_topic_key', 'idx_queue_message_succeeded_cleanup')"))
        .isEqualTo(3L);

    transactionTemplate().executeWithoutResult(status -> {
      publisher.publish("learner-profile.test", "43", new Payload(8L));
      status.setRollbackOnly();
    });
    assertThat(count("queue_message")).isEqualTo(1L);
    assertThatThrownBy(() -> execute(
        "INSERT INTO queue_message (topic, message_key, message_value, status, succeeded_at) VALUES ('x', 'y', '{}', 'SUCCEEDED', NULL)"))
        .isInstanceOf(Exception.class);
  }

  private PostgresQueuePublisher publisher() throws Exception {
    PersistentQueueProperties properties = new PersistentQueueProperties();
    MyBatisQueueMessageRepository repository = new MyBatisQueueMessageRepository(
        sqlSessionTemplate("mapper/queue/QueueMessageMapper.xml").getMapper(QueueMessageMapper.class));
    return new PostgresQueuePublisher(new ObjectMapper(), repository, properties);
  }

  private record Payload(long reviewId) {
  }
}
