package org.congcong.algomentor.queue.dispatch;

/** 消费回调失败后的持久化结果。 */
public enum QueueFailureDisposition {
  RETRY_SCHEDULED,
  TERMINAL_FAILURE
}
