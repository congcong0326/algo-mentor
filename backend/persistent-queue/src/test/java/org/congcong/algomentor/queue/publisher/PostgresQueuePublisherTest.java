package org.congcong.algomentor.queue.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.junit.jupiter.api.Test;

class PostgresQueuePublisherTest {

  @Test
  void serializesAndPersistsPendingPayloadWithinUtf8Limit() {
    PersistentQueueProperties properties = new PersistentQueueProperties();
    properties.getMessage().setMaxValueBytes(5);
    FakeRepository repository = new FakeRepository();
    QueueMessage message = new PostgresQueuePublisher(new ObjectMapper(), repository, properties)
        .publish("profile.review", "7", "abc");

    assertThat(message.value()).isEqualTo("\"abc\"");
    assertThat(repository.messages).hasSize(1);
  }

  @Test
  void rejectsInvalidOrOversizedMessagesBeforeStorage() {
    PersistentQueueProperties properties = new PersistentQueueProperties();
    properties.getMessage().setMaxValueBytes(5);
    FakeRepository repository = new FakeRepository();
    PostgresQueuePublisher publisher = new PostgresQueuePublisher(new ObjectMapper(), repository, properties);

    assertThatThrownBy(() -> publisher.publish(" ", "7", "abc")).isInstanceOf(QueuePublishException.class);
    assertThatThrownBy(() -> publisher.publish("profile.review", "7", "abcd")).isInstanceOf(QueuePublishException.class);
    assertThat(repository.messages).isEmpty();
  }

  private static final class FakeRepository implements QueueMessageRepository {
    private final java.util.ArrayList<QueueMessage> messages = new java.util.ArrayList<>();

    @Override
    public QueueMessage insertPending(String topic, String key, String value) {
      QueueMessage message = new QueueMessage(messages.size() + 1L, topic, key, value, Instant.EPOCH);
      messages.add(message);
      return message;
    }

    @Override
    public Optional<QueueMessage> findById(long messageId) {
      return messages.stream().filter(message -> message.messageId() == messageId).findFirst();
    }

    @Override
    public List<QueueMessage> findPendingByTopicAndKey(String topic, String key, int limit) {
      return messages.stream().filter(message -> message.topic().equals(topic) && message.key().equals(key)).toList();
    }

    @Override
    public List<org.congcong.algomentor.queue.dispatch.QueueEligibleKey> findEligibleKeys(String topic, int batchSize) {
      return List.of();
    }

    @Override
    public int markSucceeded(List<Long> messageIds) {
      return messageIds.size();
    }

    @Override
    public long countPendingByTopic(String topic) {
      return messages.stream().filter(message -> message.topic().equals(topic)).count();
    }

    @Override
    public Optional<Instant> findOldestPendingCreatedAt(String topic) {
      return messages.stream().filter(message -> message.topic().equals(topic)).map(QueueMessage::createdAt).min(Instant::compareTo);
    }

    @Override
    public int deleteSucceededBefore(Instant cutoff, int limit) {
      return 0;
    }
  }
}
