package org.congcong.algomentor.api.queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.consumer.BatchConsumerPolicy;
import org.congcong.algomentor.queue.consumer.BatchQueueConsumer;
import org.congcong.algomentor.queue.consumer.QueueConsumerRegistry;
import org.congcong.algomentor.queue.dispatch.QueueDequeueService;
import org.congcong.algomentor.queue.dispatch.QueueDispatchOutcome;
import org.congcong.algomentor.queue.dispatch.QueueDispatcher;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.postgres.MyBatisQueueMessageRepository;
import org.congcong.algomentor.queue.postgres.QueueMessageMapper;
import org.congcong.algomentor.queue.publisher.PostgresQueuePublisher;
import org.junit.jupiter.api.Test;

class PersistentQueueDispatchIT extends PostgresIntegrationTestSupport {

  @Test
  void dispatchesOnlyAFullTopicAndKeyBatchAndConfirmsSucceededAfterCallback() throws Exception {
    migrateLatest();
    MyBatisQueueMessageRepository repository = repository();
    PostgresQueuePublisher publisher = new PostgresQueuePublisher(new ObjectMapper(), repository, new PersistentQueueProperties());
    List<List<Long>> delivered = new ArrayList<>();
    BatchQueueConsumer consumer = new BatchQueueConsumer() {
      @Override
      public Set<String> topics() {
        return Set.of("learner-profile.test");
      }

      @Override
      public BatchConsumerPolicy policy() {
        return new BatchConsumerPolicy(5);
      }

      @Override
      public void consume(List<QueueMessage> messages) {
        try {
          assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE id IN (?, ?, ?, ?, ?) AND status = 'PROCESSING'",
              messages.get(0).messageId(), messages.get(1).messageId(), messages.get(2).messageId(),
              messages.get(3).messageId(), messages.get(4).messageId())).isEqualTo(5L);
        } catch (Exception exception) {
          throw new IllegalStateException(exception);
        }
        delivered.add(messages.stream().map(QueueMessage::messageId).toList());
      }
    };
    QueueDispatcher dispatcher = new QueueDispatcher(
        new QueueConsumerRegistry(List.of(), List.of(consumer)),
        repository,
        new QueueDequeueService(repository, transactionTemplate()));

    for (int index = 0; index < 4; index++) {
      publisher.publish("learner-profile.test", "42", new Payload(index));
    }
    assertThat(dispatcher.dispatchRound("learner-profile.test"))
        .containsExactly(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'PENDING'")).isEqualTo(4L);

    publisher.publish("learner-profile.test", "42", new Payload(4));
    assertThat(dispatcher.dispatchRound("learner-profile.test"))
        .containsExactly(QueueDispatchOutcome.DISPATCHED);
    assertThat(delivered).hasSize(1);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'SUCCEEDED'")).isEqualTo(5L);

    publisher.publish("learner-profile.test", "42", new Payload(5));
    assertThat(dispatcher.dispatchRound("learner-profile.test"))
        .containsExactly(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'PENDING'")).isEqualTo(1L);
  }

  @Test
  void retainsFailedBatchForRetryInsteadOfPrematureSuccess() throws Exception {
    migrateLatest();
    MyBatisQueueMessageRepository repository = repository();
    PersistentQueueProperties properties = new PersistentQueueProperties();
    properties.getConsumer().setMaxAttempts(2);
    PostgresQueuePublisher publisher = new PostgresQueuePublisher(new ObjectMapper(), repository, properties);
    BatchQueueConsumer failingConsumer = new BatchQueueConsumer() {
      @Override public Set<String> topics() { return Set.of("learner-profile.retry"); }
      @Override public BatchConsumerPolicy policy() { return new BatchConsumerPolicy(5); }
      @Override public void consume(List<QueueMessage> messages) { throw new IllegalStateException("expected"); }
    };
    QueueDispatcher dispatcher = new QueueDispatcher(
        new QueueConsumerRegistry(List.of(), List.of(failingConsumer)), repository,
        new QueueDequeueService(repository, transactionTemplate(), properties.getConsumer()));

    for (int index = 0; index < 5; index++) publisher.publish("learner-profile.retry", "42", new Payload(index));

    assertThat(dispatcher.dispatchRound("learner-profile.retry"))
        .containsExactly(QueueDispatchOutcome.CALLBACK_RETRY_SCHEDULED);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'PENDING' AND delivery_attempt = 1")).isEqualTo(5L);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'SUCCEEDED'")).isZero();
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'FAILED'")).isZero();
  }

  @Test
  void marksBatchFailedAfterMaximumAttemptsInsteadOfSkippingIt() throws Exception {
    migrateLatest();
    MyBatisQueueMessageRepository repository = repository();
    PersistentQueueProperties properties = new PersistentQueueProperties();
    properties.getConsumer().setMaxAttempts(2);
    PostgresQueuePublisher publisher = new PostgresQueuePublisher(new ObjectMapper(), repository, properties);
    BatchQueueConsumer failingConsumer = new BatchQueueConsumer() {
      @Override public Set<String> topics() { return Set.of("learner-profile.terminal"); }
      @Override public BatchConsumerPolicy policy() { return new BatchConsumerPolicy(5); }
      @Override public void consume(List<QueueMessage> messages) { throw new IllegalStateException("expected"); }
    };
    QueueDispatcher dispatcher = new QueueDispatcher(
        new QueueConsumerRegistry(List.of(), List.of(failingConsumer)), repository,
        new QueueDequeueService(repository, transactionTemplate(), properties.getConsumer()));

    for (int index = 0; index < 5; index++) publisher.publish("learner-profile.terminal", "42", new Payload(index));
    assertThat(dispatcher.dispatchRound("learner-profile.terminal"))
        .containsExactly(QueueDispatchOutcome.CALLBACK_RETRY_SCHEDULED);
    execute("UPDATE queue_message SET available_at = NOW() WHERE topic = 'learner-profile.terminal'");

    assertThat(dispatcher.dispatchRound("learner-profile.terminal"))
        .containsExactly(QueueDispatchOutcome.CALLBACK_TERMINAL_FAILURE);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'FAILED' AND delivery_attempt = 2")).isEqualTo(5L);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'PENDING'")).isZero();
    assertThat(dispatcher.takeTerminalFailure("learner-profile.terminal"))
        .hasValueSatisfying(failure -> assertThat(failure.messageCount()).isEqualTo(5));
  }

  private MyBatisQueueMessageRepository repository() throws Exception {
    return new MyBatisQueueMessageRepository(
        sqlSessionTemplate("mapper/queue/QueueMessageMapper.xml").getMapper(QueueMessageMapper.class));
  }

  private record Payload(int value) {
  }
}
