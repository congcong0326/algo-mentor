package org.congcong.algomentor.queue.dispatch;

import java.util.List;
import java.util.UUID;
import org.congcong.algomentor.queue.model.QueueMessage;

/** 一次已持久化领取的消息批次；lease token 用于限制确认和失败回写的所有权。 */
public record QueueDelivery(List<QueueMessage> messages, UUID leaseToken, int attempt) {

  public QueueDelivery {
    messages = messages == null ? List.of() : List.copyOf(messages);
    if (messages.isEmpty() || leaseToken == null || attempt < 1) {
      throw new IllegalArgumentException("Invalid persistent queue delivery");
    }
  }

  public List<Long> messageIds() {
    return messages.stream().map(QueueMessage::messageId).toList();
  }
}
