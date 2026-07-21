package org.congcong.algomentor.queue.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.junit.jupiter.api.Test;

class QueueConsumerRegistryTest {

  @Test
  void registersMultipleTopicsAndRejectsEveryTopicConflict() {
    QueueConsumer first = consumer("one", "two");
    QueueConsumerRegistry registry = new QueueConsumerRegistry(List.of(first), List.of());

    assertThat(registry.topics()).containsExactlyInAnyOrder("one", "two");
    assertThat(registry.registration("one").isBatch()).isFalse();
    assertThatThrownBy(() -> new QueueConsumerRegistry(List.of(first, consumer("two")), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QueueConsumerRegistry(List.of(first), List.of(batchConsumer("two", 2))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankTopicsAndInvalidBatchPolicy() {
    assertThatThrownBy(() -> new BatchConsumerPolicy(1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QueueConsumerRegistry(List.of(consumer(" ")), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QueueConsumerRegistry(List.of(), List.of(batchConsumer("", 2))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private QueueConsumer consumer(String... topics) {
    return new QueueConsumer() {
      @Override
      public Set<String> topics() {
        return Set.of(topics);
      }

      @Override
      public void consume(QueueMessage message) {
      }
    };
  }

  private BatchQueueConsumer batchConsumer(String topic, int batchSize) {
    return new BatchQueueConsumer() {
      @Override
      public Set<String> topics() {
        return Set.of(topic);
      }

      @Override
      public BatchConsumerPolicy policy() {
        return new BatchConsumerPolicy(batchSize);
      }

      @Override
      public void consume(List<QueueMessage> messages) {
      }
    };
  }
}
