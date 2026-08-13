package org.congcong.algomentor.queue.dispatch;

/** 一次 key 派发的结果。 */
public enum QueueDispatchOutcome {
  NO_ELIGIBLE_KEY,
  DISPATCHED,
  DEQUEUE_FAILED,
  CALLBACK_RETRY_SCHEDULED,
  CALLBACK_TERMINAL_FAILURE,
  ACKNOWLEDGEMENT_FAILED
}
