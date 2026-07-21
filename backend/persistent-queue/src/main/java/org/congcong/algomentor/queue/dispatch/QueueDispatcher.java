package org.congcong.algomentor.queue.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.congcong.algomentor.queue.consumer.QueueConsumerRegistration;
import org.congcong.algomentor.queue.consumer.QueueConsumerRegistry;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;

/** 每轮按最早消息轮转所有 key；不负责线程或等待。 */
public class QueueDispatcher {
  private final QueueConsumerRegistry registry;
  private final QueueMessageRepository repository;
  private final QueueDequeueService dequeueService;
  public QueueDispatcher(QueueConsumerRegistry registry, QueueMessageRepository repository, QueueDequeueService dequeueService) {
    this.registry = registry; this.repository = repository; this.dequeueService = dequeueService;
  }
  public List<QueueDispatchOutcome> dispatchRound(String topic) {
    return dispatchRound(topic, () -> true);
  }

  /** 运行时停止后不再开始下一个 key 的 dequeue。 */
  public List<QueueDispatchOutcome> dispatchRound(String topic, BooleanSupplier mayDequeue) {
    QueueConsumerRegistration registration = registry.registration(topic);
    if (registration == null) return List.of(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    List<QueueEligibleKey> keys = repository.findEligibleKeys(topic, registration.batchSize());
    if (keys.isEmpty()) return List.of(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    List<QueueDispatchOutcome> outcomes = new ArrayList<>();
    for (QueueEligibleKey key : keys) {
      if (!mayDequeue.getAsBoolean()) break;
      List<QueueMessage> messages = dequeueService.dequeue(topic, key.key(), registration.batchSize());
      if (messages.isEmpty()) { outcomes.add(QueueDispatchOutcome.DEQUEUE_FAILED); continue; }
      try {
        if (registration.isBatch()) registration.batchConsumer().consume(messages);
        else registration.consumer().consume(messages.get(0));
        outcomes.add(QueueDispatchOutcome.DISPATCHED);
      } catch (RuntimeException exception) { outcomes.add(QueueDispatchOutcome.CALLBACK_FAILED); }
    }
    return List.copyOf(outcomes);
  }
}
