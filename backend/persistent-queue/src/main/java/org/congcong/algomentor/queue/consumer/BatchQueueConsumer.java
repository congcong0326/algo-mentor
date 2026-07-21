package org.congcong.algomentor.queue.consumer;

import java.util.List;
import java.util.Set;
import org.congcong.algomentor.queue.model.QueueMessage;

/** 同 topic/key 严格满批消费者。 */
public interface BatchQueueConsumer {
  Set<String> topics();
  BatchConsumerPolicy policy();
  void consume(List<QueueMessage> messages);
}
