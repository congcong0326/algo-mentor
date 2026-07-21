package org.congcong.algomentor.queue.consumer;

/** topic 的唯一消费者注册信息。 */
public record QueueConsumerRegistration(String consumerName, String topic, int batchSize, QueueConsumer consumer, BatchQueueConsumer batchConsumer) {
  public QueueConsumerRegistration {
    if (consumerName == null || consumerName.isBlank() || topic == null || topic.isBlank() || batchSize < 1
        || (consumer == null) == (batchConsumer == null)) {
      throw new IllegalArgumentException("Invalid queue consumer registration");
    }
  }
  public boolean isBatch() { return batchConsumer != null; }
}
