package org.congcong.algomentor.queue.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.congcong.algomentor.queue.consumer.BatchConsumerPolicy;
import org.congcong.algomentor.queue.consumer.BatchQueueConsumer;
import org.congcong.algomentor.queue.consumer.QueueConsumer;
import org.congcong.algomentor.queue.consumer.QueueConsumerRegistry;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class QueueDispatcherTest {

  @Test
  void dispatchesOneStrictBatchPerKeyInOldestKeyOrder() {
    FakeRepository repository = new FakeRepository();
    repository.pending("topic", "hot", 5);
    repository.pending("topic", "cool", 5);
    List<List<Long>> batches = new ArrayList<>();
    BatchQueueConsumer consumer = new BatchQueueConsumer() {
      @Override
      public Set<String> topics() {
        return Set.of("topic");
      }

      @Override
      public BatchConsumerPolicy policy() {
        return new BatchConsumerPolicy(5);
      }

      @Override
      public void consume(List<QueueMessage> messages) {
        batches.add(messages.stream().map(QueueMessage::messageId).toList());
      }
    };

    List<QueueDispatchOutcome> outcomes = dispatcher(repository, List.of(), List.of(consumer)).dispatchRound("topic");

    assertThat(outcomes).containsExactly(QueueDispatchOutcome.DISPATCHED, QueueDispatchOutcome.DISPATCHED);
    assertThat(batches).hasSize(2);
    assertThat(batches.get(0)).hasSize(5);
    assertThat(batches.get(1)).hasSize(5);
    assertThat(repository.pendingMessages()).isEmpty();
  }

  @Test
  void continuesAfterCallbackFailureForOtherEligibleKeys() {
    FakeRepository repository = new FakeRepository();
    repository.pending("topic", "short", 4);
    repository.pending("topic", "bad", 1);
    repository.pending("topic", "good", 1);
    List<Long> delivered = new ArrayList<>();
    QueueConsumer failing = singleConsumer("topic", message -> {
      if (message.key().equals("bad")) {
        throw new IllegalStateException("expected");
      }
      delivered.add(message.messageId());
    });

    List<QueueDispatchOutcome> outcomes = dispatcher(repository, List.of(failing), List.of()).dispatchRound("topic");

    assertThat(outcomes).containsExactly(
        QueueDispatchOutcome.DISPATCHED, QueueDispatchOutcome.CALLBACK_FAILED, QueueDispatchOutcome.DISPATCHED);
    assertThat(delivered).hasSize(2);
    assertThat(repository.pendingMessages()).extracting(QueueMessage::key).containsOnly("short");
    assertThat(repository.pendingMessages()).hasSize(3);
    assertThat(repository.succeededMessages()).hasSize(3);
  }

  @Test
  void leavesUnderfilledBatchPending() {
    FakeRepository repository = new FakeRepository();
    repository.pending("topic", "short", 4);
    BatchQueueConsumer consumer = new BatchQueueConsumer() {
      @Override
      public Set<String> topics() {
        return Set.of("topic");
      }

      @Override
      public BatchConsumerPolicy policy() {
        return new BatchConsumerPolicy(5);
      }

      @Override
      public void consume(List<QueueMessage> messages) {
      }
    };

    List<QueueDispatchOutcome> outcomes = dispatcher(repository, List.of(), List.of(consumer)).dispatchRound("topic");

    assertThat(outcomes).containsExactly(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    assertThat(repository.pendingMessages()).hasSize(4);
    assertThat(repository.succeededMessages()).isEmpty();
  }

  @Test
  void dequeueFailureNeverInvokesTheCallback() {
    FakeRepository repository = new FakeRepository();
    repository.pending("topic", "key", 1);
    repository.markCountOverride = 0;
    List<QueueMessage> delivered = new ArrayList<>();

    List<QueueDispatchOutcome> outcomes = dispatcher(
        repository, List.of(singleConsumer("topic", delivered::add)), List.of()).dispatchRound("topic");

    assertThat(outcomes).containsExactly(QueueDispatchOutcome.DEQUEUE_FAILED);
    assertThat(delivered).isEmpty();
    assertThat(repository.pendingMessages()).hasSize(1);
  }

  private QueueDispatcher dispatcher(
      FakeRepository repository, List<QueueConsumer> consumers, List<BatchQueueConsumer> batchConsumers) {
    return new QueueDispatcher(
        new QueueConsumerRegistry(consumers, batchConsumers),
        repository,
        new QueueDequeueService(repository, new TransactionTemplate(new NoOpTransactionManager())));
  }

  private QueueConsumer singleConsumer(String topic, java.util.function.Consumer<QueueMessage> callback) {
    return new QueueConsumer() {
      @Override
      public Set<String> topics() {
        return Set.of(topic);
      }

      @Override
      public void consume(QueueMessage message) {
        callback.accept(message);
      }
    };
  }

  private static final class FakeRepository implements QueueMessageRepository {
    private final List<QueueMessage> pending = new ArrayList<>();
    private final List<QueueMessage> succeeded = new ArrayList<>();
    private long nextId = 1;
    private Integer markCountOverride;

    void pending(String topic, String key, int count) {
      for (int index = 0; index < count; index++) {
        pending.add(new QueueMessage(nextId++, topic, key, "{}", Instant.EPOCH));
      }
    }

    @Override
    public QueueMessage insertPending(String topic, String key, String value) {
      QueueMessage message = new QueueMessage(nextId++, topic, key, value, Instant.EPOCH);
      pending.add(message);
      return message;
    }

    @Override
    public Optional<QueueMessage> findById(long messageId) {
      return java.util.stream.Stream.concat(pending.stream(), succeeded.stream())
          .filter(message -> message.messageId() == messageId).findFirst();
    }

    @Override
    public List<QueueMessage> findPendingByTopicAndKey(String topic, String key, int limit) {
      return pending.stream().filter(message -> message.topic().equals(topic) && message.key().equals(key))
          .sorted(Comparator.comparingLong(QueueMessage::messageId)).limit(limit).toList();
    }

    @Override
    public List<QueueEligibleKey> findEligibleKeys(String topic, int batchSize) {
      return pending.stream().filter(message -> message.topic().equals(topic)).collect(java.util.stream.Collectors.groupingBy(
          QueueMessage::key)).entrySet().stream().filter(entry -> entry.getValue().size() >= batchSize)
          .map(entry -> new QueueEligibleKey(entry.getKey(), entry.getValue().stream()
              .mapToLong(QueueMessage::messageId).min().orElseThrow()))
          .sorted(Comparator.comparingLong(QueueEligibleKey::oldestMessageId)).toList();
    }

    @Override
    public int markSucceeded(List<Long> messageIds) {
      if (markCountOverride != null) {
        return markCountOverride;
      }
      List<QueueMessage> selected = pending.stream().filter(message -> messageIds.contains(message.messageId())).toList();
      pending.removeAll(selected);
      succeeded.addAll(selected);
      return selected.size();
    }

    @Override
    public long countPendingByTopic(String topic) {
      return pending.stream().filter(message -> message.topic().equals(topic)).count();
    }

    @Override
    public Optional<Instant> findOldestPendingCreatedAt(String topic) {
      return pending.stream().filter(message -> message.topic().equals(topic)).map(QueueMessage::createdAt).min(Instant::compareTo);
    }

    @Override
    public int deleteSucceededBefore(Instant cutoff, int limit) {
      return 0;
    }

    List<QueueMessage> pendingMessages() {
      return List.copyOf(pending);
    }

    List<QueueMessage> succeededMessages() {
      return List.copyOf(succeeded);
    }
  }

  private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
    }
  }
}
