package org.congcong.algomentor.queue.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
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
  void confirmsOneStrictBatchPerKeyOnlyAfterEachCallbackSucceeds() {
    FakeRepository repository = new FakeRepository();
    repository.pending("topic", "hot", 5);
    repository.pending("topic", "cool", 5);
    List<List<Long>> batches = new ArrayList<>();
    BatchQueueConsumer consumer = new BatchQueueConsumer() {
      @Override public Set<String> topics() { return Set.of("topic"); }
      @Override public BatchConsumerPolicy policy() { return new BatchConsumerPolicy(5); }
      @Override public void consume(List<QueueMessage> messages) {
        batches.add(messages.stream().map(QueueMessage::messageId).toList());
      }
    };

    List<QueueDispatchOutcome> outcomes = dispatcher(repository, List.of(), List.of(consumer)).dispatchRound("topic");

    assertThat(outcomes).containsExactly(QueueDispatchOutcome.DISPATCHED, QueueDispatchOutcome.DISPATCHED);
    assertThat(batches).hasSize(2);
    assertThat(repository.pendingMessages()).isEmpty();
    assertThat(repository.succeededMessages()).hasSize(10);
  }

  @Test
  void retainsFailedDeliveryForRetryAndContinuesOtherKeys() {
    FakeRepository repository = new FakeRepository();
    repository.pending("topic", "short", 4);
    repository.pending("topic", "bad", 1);
    repository.pending("topic", "good", 1);
    List<Long> delivered = new ArrayList<>();
    QueueConsumer failing = singleConsumer("topic", message -> {
      if (message.key().equals("bad")) throw new IllegalStateException("expected");
      delivered.add(message.messageId());
    });

    List<QueueDispatchOutcome> outcomes = dispatcher(repository, List.of(failing), List.of()).dispatchRound("topic");

    assertThat(outcomes).containsExactly(
        QueueDispatchOutcome.DISPATCHED, QueueDispatchOutcome.CALLBACK_RETRY_SCHEDULED, QueueDispatchOutcome.DISPATCHED);
    assertThat(delivered).hasSize(2);
    assertThat(repository.pendingMessages()).extracting(QueueMessage::key)
        .containsExactlyInAnyOrder("short", "short", "short", "bad");
    assertThat(repository.succeededMessages()).extracting(QueueMessage::key).containsExactly("short", "good");
  }

  @Test
  void stopsRoundAfterMaximumDeliveryAttemptsAndLeavesTerminalFailureInspectable() {
    FakeRepository repository = new FakeRepository();
    repository.pending("topic", "bad", 1);
    PersistentQueueProperties properties = new PersistentQueueProperties();
    properties.getConsumer().setMaxAttempts(1);
    QueueDispatcher dispatcher = dispatcher(repository, List.of(singleConsumer("topic", ignored -> {
      throw new IllegalStateException("expected");
    })), List.of(), properties);

    assertThat(dispatcher.dispatchRound("topic")).containsExactly(QueueDispatchOutcome.CALLBACK_TERMINAL_FAILURE);
    assertThat(repository.failedMessages()).hasSize(1);
    assertThat(dispatcher.takeTerminalFailure("topic"))
        .hasValueSatisfying(failure -> assertThat(failure.attempts()).isEqualTo(1));
  }

  @Test
  void leavesUnderfilledBatchPending() {
    FakeRepository repository = new FakeRepository();
    repository.pending("topic", "short", 4);
    BatchQueueConsumer consumer = new BatchQueueConsumer() {
      @Override public Set<String> topics() { return Set.of("topic"); }
      @Override public BatchConsumerPolicy policy() { return new BatchConsumerPolicy(5); }
      @Override public void consume(List<QueueMessage> messages) { }
    };

    assertThat(dispatcher(repository, List.of(), List.of(consumer)).dispatchRound("topic"))
        .containsExactly(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    assertThat(repository.pendingMessages()).hasSize(4);
  }

  @Test
  void claimFailureNeverInvokesTheCallback() {
    FakeRepository repository = new FakeRepository();
    repository.pending("topic", "key", 1);
    repository.claimCountOverride = 0;
    List<QueueMessage> delivered = new ArrayList<>();

    assertThat(dispatcher(repository, List.of(singleConsumer("topic", delivered::add)), List.of()).dispatchRound("topic"))
        .containsExactly(QueueDispatchOutcome.DEQUEUE_FAILED);
    assertThat(delivered).isEmpty();
    assertThat(repository.pendingMessages()).hasSize(1);
  }

  private QueueDispatcher dispatcher(
      FakeRepository repository, List<QueueConsumer> consumers, List<BatchQueueConsumer> batchConsumers) {
    return dispatcher(repository, consumers, batchConsumers, new PersistentQueueProperties());
  }

  private QueueDispatcher dispatcher(
      FakeRepository repository,
      List<QueueConsumer> consumers,
      List<BatchQueueConsumer> batchConsumers,
      PersistentQueueProperties properties) {
    return new QueueDispatcher(
        new QueueConsumerRegistry(consumers, batchConsumers),
        repository,
        new QueueDequeueService(repository, new TransactionTemplate(new NoOpTransactionManager()), properties.getConsumer()));
  }

  private QueueConsumer singleConsumer(String topic, java.util.function.Consumer<QueueMessage> callback) {
    return new QueueConsumer() {
      @Override public Set<String> topics() { return Set.of(topic); }
      @Override public void consume(QueueMessage message) { callback.accept(message); }
    };
  }

  private static final class FakeRepository implements QueueMessageRepository {
    private final List<QueueMessage> pending = new ArrayList<>();
    private final List<QueueMessage> processing = new ArrayList<>();
    private final List<QueueMessage> succeeded = new ArrayList<>();
    private final List<QueueMessage> failed = new ArrayList<>();
    private final java.util.Map<Long, UUID> leases = new java.util.HashMap<>();
    private long nextId = 1;
    private Integer claimCountOverride;

    void pending(String topic, String key, int count) {
      for (int index = 0; index < count; index++) pending.add(new QueueMessage(nextId++, topic, key, "{}", Instant.EPOCH));
    }

    @Override public QueueMessage insertPending(String topic, String key, String value) {
      QueueMessage message = new QueueMessage(nextId++, topic, key, value, Instant.EPOCH);
      pending.add(message);
      return message;
    }
    @Override public Optional<QueueMessage> findById(long messageId) {
      return java.util.stream.Stream.of(pending, processing, succeeded, failed).flatMap(List::stream)
          .filter(message -> message.messageId() == messageId).findFirst();
    }
    @Override public List<QueueMessage> findPendingByTopicAndKey(String topic, String key, int limit) {
      return pending.stream().filter(message -> message.topic().equals(topic) && message.key().equals(key))
          .sorted(Comparator.comparingLong(QueueMessage::messageId)).limit(limit).toList();
    }
    @Override public List<QueueEligibleKey> findEligibleKeys(String topic, int batchSize) {
      return pending.stream().filter(message -> message.topic().equals(topic)).collect(java.util.stream.Collectors.groupingBy(
          QueueMessage::key)).entrySet().stream().filter(entry -> entry.getValue().size() >= batchSize)
          .map(entry -> new QueueEligibleKey(entry.getKey(), entry.getValue().stream()
              .mapToLong(QueueMessage::messageId).min().orElseThrow()))
          .sorted(Comparator.comparingLong(QueueEligibleKey::oldestMessageId)).toList();
    }
    @Override public int reclaimExpiredProcessing(Instant now) { return 0; }
    @Override public int claimPending(List<Long> messageIds, UUID leaseToken, Instant leaseExpiresAt, Instant now) {
      if (claimCountOverride != null) return claimCountOverride;
      List<QueueMessage> selected = pending.stream().filter(message -> messageIds.contains(message.messageId())).toList();
      pending.removeAll(selected);
      List<QueueMessage> claimed = selected.stream().map(message -> new QueueMessage(
          message.messageId(), message.topic(), message.key(), message.value(), message.createdAt(), message.deliveryAttempt() + 1)).toList();
      processing.addAll(claimed);
      claimed.forEach(message -> leases.put(message.messageId(), leaseToken));
      return claimed.size();
    }
    @Override public int markSucceeded(List<Long> messageIds, UUID leaseToken) {
      List<QueueMessage> selected = processing.stream()
          .filter(message -> messageIds.contains(message.messageId()) && leaseToken.equals(leases.get(message.messageId()))).toList();
      processing.removeAll(selected);
      selected.forEach(message -> leases.remove(message.messageId()));
      succeeded.addAll(selected);
      return selected.size();
    }
    @Override public int retryOrFail(
        List<Long> messageIds, UUID leaseToken, Instant retryAt, Instant now, int maxAttempts, String errorType) {
      List<QueueMessage> selected = processing.stream()
          .filter(message -> messageIds.contains(message.messageId()) && leaseToken.equals(leases.get(message.messageId()))).toList();
      processing.removeAll(selected);
      selected.forEach(message -> leases.remove(message.messageId()));
      for (QueueMessage message : selected) {
        if (message.deliveryAttempt() >= maxAttempts) failed.add(message); else pending.add(message);
      }
      return selected.size();
    }
    @Override public long countPendingByTopic(String topic) { return pending.stream().filter(message -> message.topic().equals(topic)).count(); }
    @Override public long countFailedByTopic(String topic) { return failed.stream().filter(message -> message.topic().equals(topic)).count(); }
    @Override public Optional<Instant> findOldestPendingCreatedAt(String topic) {
      return pending.stream().filter(message -> message.topic().equals(topic)).map(QueueMessage::createdAt).min(Instant::compareTo);
    }
    @Override public int deleteSucceededBefore(Instant cutoff, int limit) { return 0; }
    List<QueueMessage> pendingMessages() { return List.copyOf(pending); }
    List<QueueMessage> succeededMessages() { return List.copyOf(succeeded); }
    List<QueueMessage> failedMessages() { return List.copyOf(failed); }
  }

  private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
    @Override protected void doCommit(DefaultTransactionStatus status) throws TransactionException { }
    @Override protected void doRollback(DefaultTransactionStatus status) throws TransactionException { }
  }
}
