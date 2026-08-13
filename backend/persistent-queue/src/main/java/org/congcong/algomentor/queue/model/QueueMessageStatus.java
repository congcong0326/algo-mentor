package org.congcong.algomentor.queue.model;

/** 消息处理状态；只有业务处理成功并确认后才能进入 SUCCEEDED。 */
public enum QueueMessageStatus {
  PENDING,
  PROCESSING,
  SUCCEEDED,
  FAILED
}
