package org.congcong.algomentor.queue.consumer;

/** 仅支持严格满批阈值，不包含延迟或重试策略。 */
public record BatchConsumerPolicy(int batchSize) {
  public BatchConsumerPolicy {
    if (batchSize < 2) {
      throw new IllegalArgumentException("Queue batch size must be greater than one");
    }
  }
}
