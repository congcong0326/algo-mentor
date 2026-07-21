package org.congcong.algomentor.queue.publisher;

import org.congcong.algomentor.queue.model.QueueMessage;

/** 向单播持久化队列发布 JSON 消息的端口。 */
public interface QueuePublisher {

  QueueMessage publish(String topic, String key, Object payload);
}
