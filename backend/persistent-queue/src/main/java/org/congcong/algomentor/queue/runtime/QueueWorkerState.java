package org.congcong.algomentor.queue.runtime;

/** 队列 topic worker 的内部运行状态，仅用于日志和指标。 */
public enum QueueWorkerState {
  STARTING,
  RUNNING,
  STOPPING,
  STOPPED,
  FAILED_RETRYING,
  FAILED_STOPPED
}
