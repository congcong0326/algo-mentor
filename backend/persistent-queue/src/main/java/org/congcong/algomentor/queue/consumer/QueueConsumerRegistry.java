package org.congcong.algomentor.queue.consumer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 启动期建立不可变 topic 路由，任何冲突均阻止应用启动。 */
public class QueueConsumerRegistry {
  private final Map<String, QueueConsumerRegistration> registrations;

  public QueueConsumerRegistry(List<QueueConsumer> consumers, List<BatchQueueConsumer> batchConsumers) {
    Map<String, QueueConsumerRegistration> entries = new LinkedHashMap<>();
    for (QueueConsumer consumer : consumers == null ? List.<QueueConsumer>of() : consumers) {
      register(entries, consumer.getClass().getName(), consumer.topics(), 1, consumer, null);
    }
    for (BatchQueueConsumer consumer : batchConsumers == null ? List.<BatchQueueConsumer>of() : batchConsumers) {
      register(entries, consumer.getClass().getName(), consumer.topics(), consumer.policy().batchSize(), null, consumer);
    }
    registrations = Map.copyOf(entries);
  }

  public QueueConsumerRegistration registration(String topic) { return registrations.get(topic); }
  public Set<String> topics() { return registrations.keySet(); }

  private void register(Map<String, QueueConsumerRegistration> entries, String name, Set<String> topics, int batchSize,
      QueueConsumer consumer, BatchQueueConsumer batchConsumer) {
    if (topics == null || topics.isEmpty()) { throw new IllegalArgumentException("Queue consumer must declare topics"); }
    for (String topic : topics) {
      if (topic == null || topic.isBlank() || entries.putIfAbsent(topic.trim(),
          new QueueConsumerRegistration(name, topic.trim(), batchSize, consumer, batchConsumer)) != null) {
        throw new IllegalArgumentException("Queue topic is registered by more than one consumer: " + topic);
      }
    }
  }
}
