package org.congcong.algomentor.queue.dispatch;

import java.util.List;
import org.congcong.algomentor.queue.model.QueueMessage;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.springframework.transaction.support.TransactionTemplate;

/** 在短事务内选择消息并标记 SUCCEEDED，回调由事务提交后调用方负责。 */
public class QueueDequeueService {
  private final QueueMessageRepository repository;
  private final TransactionTemplate transactionTemplate;
  public QueueDequeueService(QueueMessageRepository repository, TransactionTemplate transactionTemplate) {
    this.repository = repository; this.transactionTemplate = transactionTemplate;
  }
  public List<QueueMessage> dequeue(String topic, String key, int count) {
    return transactionTemplate.execute(status -> {
      List<QueueMessage> messages = repository.findPendingByTopicAndKey(topic, key, count);
      if (messages.size() != count || repository.markSucceeded(messages.stream().map(QueueMessage::messageId).toList()) != count) {
        status.setRollbackOnly(); return List.of();
      }
      return messages;
    });
  }
}
