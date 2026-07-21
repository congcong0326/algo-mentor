package org.congcong.algomentor.queue.consumer;

import java.util.Set;
import org.congcong.algomentor.queue.model.QueueMessage;

/** 单条队列消费者。 */
public interface QueueConsumer {
  Set<String> topics();
  void consume(QueueMessage message);
}
