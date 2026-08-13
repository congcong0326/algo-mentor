package org.congcong.algomentor.queue.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;
import org.congcong.algomentor.queue.alert.QueueTerminalFailure;
import org.congcong.algomentor.queue.consumer.QueueConsumerRegistration;
import org.congcong.algomentor.queue.consumer.QueueConsumerRegistry;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;

/** 每轮按最早可消费消息轮转所有 key；业务成功后才确认消息。 */
public class QueueDispatcher {

  private final QueueConsumerRegistry registry;
  private final QueueMessageRepository repository;
  private final QueueDequeueService dequeueService;
  private final ConcurrentMap<String, QueueTerminalFailure> terminalFailures = new ConcurrentHashMap<>();

  public QueueDispatcher(
      QueueConsumerRegistry registry,
      QueueMessageRepository repository,
      QueueDequeueService dequeueService) {
    this.registry = registry;
    this.repository = repository;
    this.dequeueService = dequeueService;
  }

  public List<QueueDispatchOutcome> dispatchRound(String topic) {
    return dispatchRound(topic, () -> true);
  }

  /** 运行时停止后不再开始下一个 key 的领取。 */
  public List<QueueDispatchOutcome> dispatchRound(String topic, BooleanSupplier mayDequeue) {
    QueueConsumerRegistration registration = registry.registration(topic);
    if (registration == null) {
      return List.of(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    }
    dequeueService.reclaimExpiredLeases();
    List<QueueEligibleKey> keys = repository.findEligibleKeys(topic, registration.batchSize());
    if (keys.isEmpty()) {
      return List.of(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    }
    List<QueueDispatchOutcome> outcomes = new ArrayList<>();
    for (QueueEligibleKey key : keys) {
      if (!mayDequeue.getAsBoolean()) {
        break;
      }
      QueueDelivery delivery = dequeueService.dequeue(topic, key.key(), registration.batchSize());
      if (delivery == null) {
        outcomes.add(QueueDispatchOutcome.DEQUEUE_FAILED);
        continue;
      }
      try {
        if (registration.isBatch()) {
          registration.batchConsumer().consume(delivery.messages());
        } else {
          registration.consumer().consume(delivery.messages().get(0));
        }
      } catch (RuntimeException exception) {
        QueueFailureDisposition disposition = dequeueService.fail(delivery, exception);
        outcomes.add(disposition == QueueFailureDisposition.TERMINAL_FAILURE
            ? QueueDispatchOutcome.CALLBACK_TERMINAL_FAILURE
            : QueueDispatchOutcome.CALLBACK_RETRY_SCHEDULED);
        if (disposition == QueueFailureDisposition.TERMINAL_FAILURE) {
          terminalFailures.put(topic, new QueueTerminalFailure(
              topic, delivery.messages().size(), delivery.attempt(), exception.getClass().getSimpleName()));
          break;
        }
        continue;
      }
      if (dequeueService.acknowledge(delivery)) {
        outcomes.add(QueueDispatchOutcome.DISPATCHED);
      } else {
        outcomes.add(QueueDispatchOutcome.ACKNOWLEDGEMENT_FAILED);
      }
    }
    return List.copyOf(outcomes);
  }

  /** 取得并清除当前 topic 最近一次终态失败的脱敏信息。 */
  public Optional<QueueTerminalFailure> takeTerminalFailure(String topic) {
    return Optional.ofNullable(terminalFailures.remove(topic));
  }
}
