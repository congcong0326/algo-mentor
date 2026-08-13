package org.congcong.algomentor.queue.alert;

/** 达到最大消费次数后的脱敏告警载荷，不携带消息 key 或 value。 */
public record QueueTerminalFailure(String topic, int messageCount, int attempts, String errorType) {

  public QueueTerminalFailure {
    if (topic == null || topic.isBlank() || messageCount < 1 || attempts < 1
        || errorType == null || errorType.isBlank()) {
      throw new IllegalArgumentException("Invalid persistent queue terminal failure");
    }
  }
}
