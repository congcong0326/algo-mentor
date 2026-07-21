package org.congcong.algomentor.queue.publisher;

/** 发布前校验或 JSON 序列化失败时使用的稳定异常。 */
public class QueuePublishException extends RuntimeException {

  public QueuePublishException(String message) {
    super(message);
  }

  public QueuePublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
